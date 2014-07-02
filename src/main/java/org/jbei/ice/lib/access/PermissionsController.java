package org.jbei.ice.lib.access;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import org.jbei.ice.ControllerException;
import org.jbei.ice.lib.account.AccountController;
import org.jbei.ice.lib.account.model.Account;
import org.jbei.ice.lib.common.logging.Logger;
import org.jbei.ice.lib.dao.DAOException;
import org.jbei.ice.lib.dao.DAOFactory;
import org.jbei.ice.lib.dao.hibernate.FolderDAO;
import org.jbei.ice.lib.dao.hibernate.PermissionDAO;
import org.jbei.ice.lib.dto.entry.EntryType;
import org.jbei.ice.lib.dto.entry.PartData;
import org.jbei.ice.lib.dto.folder.FolderAuthorization;
import org.jbei.ice.lib.dto.permission.AccessPermission;
import org.jbei.ice.lib.entry.EntryAuthorization;
import org.jbei.ice.lib.entry.EntryCreator;
import org.jbei.ice.lib.entry.model.Entry;
import org.jbei.ice.lib.folder.Folder;
import org.jbei.ice.lib.group.Group;
import org.jbei.ice.lib.group.GroupController;

/**
 * Controller for permissions
 *
 * @author Hector Plahar
 */
public class PermissionsController {

    private final AccountController accountController;
    private final GroupController groupController;
    private final FolderDAO folderDAO;
    private final PermissionDAO dao;

    public PermissionsController() {
        accountController = new AccountController();
        groupController = new GroupController();
        folderDAO = DAOFactory.getFolderDAO();
        dao = DAOFactory.getPermissionDAO();
    }

    /**
     * Creates a new permission object for groups from the fields in the parameter.
     * Used mainly by bulk upload since permissions are set at the group level
     *
     * @param access information about the access permission
     * @return saved permission
     * @throws ControllerException
     */
    public Permission recordGroupPermission(AccessPermission access) throws ControllerException {
        try {
            Group group = groupController.getGroupById(access.getArticleId());
            if (group == null)
                throw new ControllerException("Could retrieve group for permission add");

            Permission permission = new Permission();
            permission.setGroup(group);
            permission.setCanRead(access.isCanRead());
            permission.setCanWrite(access.isCanWrite());
            return dao.create(permission);
        } catch (DAOException e) {
            Logger.error(e);
            throw new ControllerException(e);
        }
    }

    public Permission addPermission(String userId, AccessPermission access) {
        if (access.isEntry()) {
            Entry entry = DAOFactory.getEntryDAO().get(access.getTypeId());
            if (entry == null)
                throw new IllegalArgumentException("Cannot find entry " + access.getTypeId());

            EntryAuthorization authorization = new EntryAuthorization();
            authorization.expectWrite(userId, entry);
            return addPermission(access, entry, null);
        }

        if (access.isFolder()) {
            Folder folder = folderDAO.get(access.getTypeId());
            if (!hasWritePermission(userId, folder)) {
                Logger.error(userId + " not allowed to add " + access.toString());
                return null;
            }

            // propagate permissions
            if (folder.isPropagatePermissions()) {
                for (Entry folderContent : folder.getContents()) {
                    addPermission(access, folderContent, null);
                }
            }
            return addPermission(access, null, folder);
        }

        return null;
    }

    protected Permission addPermission(AccessPermission access, Entry entry, Folder folder) {
        // account or group
        Account account = null;
        Group group = null;
        switch (access.getArticle()) {
            case ACCOUNT:
            default:
                account = accountController.get(access.getArticleId());
                break;

            case GROUP:
                group = groupController.getGroupById(access.getArticleId());
                break;
        }

        // does the permissions already exists
        if (dao.hasPermission(entry, folder, account, group, access.isCanRead(), access.isCanWrite())) {
            return dao.retrievePermission(entry, folder, account, group, access.isCanRead(), access.isCanWrite());
        }

        // add the permission if not
        Permission permission = new Permission();
        permission.setEntry(entry);
        if (entry != null)
            entry.getPermissions().add(permission);
        permission.setGroup(group);
        permission.setFolder(folder);
        permission.setAccount(account);
        permission.setCanRead(access.isCanRead());
        permission.setCanWrite(access.isCanWrite());
        return dao.create(permission);
    }

    /**
     * Removes permissions that are associated with folder. This is typically for
     * folders that are shared as public folders
     *
     * @param userId   user id. typically an administrator
     * @param folderId unique identifier for folder whose permissions are to be removed
     */
    public void removeAllFolderPermissions(String userId, long folderId) {
        Folder folder = folderDAO.get(folderId);
        if (folder == null) {
            Logger.warn("Could not find folder with id " + folderId + " for permission removal");
            return;
        }

        if (!hasWritePermission(userId, folder)) {
            Logger.error(userId + " not allowed to modify folder " + folderId);
            return;
        }

        dao.clearPermissions(folder);
    }

    public void removePermission(String userId, AccessPermission access) throws ControllerException {
        if (access.isEntry()) {
            Entry entry = DAOFactory.getEntryDAO().get(access.getTypeId());
            if (entry == null)
                throw new ControllerException("Cannot find entry " + access.getTypeId());

            EntryAuthorization authorization = new EntryAuthorization();
            authorization.expectWrite(userId, entry);

            // remove permission from entry
            removePermission(access, entry, null);

        } else if (access.isFolder()) {
            Folder folder = folderDAO.get(access.getTypeId());
            if (!hasWritePermission(userId, folder))
                throw new ControllerException(userId + " not allowed to " + access.toString());

            // if folder is to be propagated, add removing permission from contained entries
            if (folder.isPropagatePermissions()) {
                for (Entry folderContent : folder.getContents()) {
                    removePermission(access, folderContent, null);
                }
            }
            // remove permission from folder
            removePermission(access, null, folder);
        }
    }

    private void removePermission(AccessPermission access, Entry entry, Folder folder) throws ControllerException {
        // account or group
        Account account = null;
        Group group = null;
        switch (access.getArticle()) {
            case ACCOUNT:
            default:
                account = accountController.get(access.getArticleId());
                break;

            case GROUP:
                group = groupController.getGroupById(access.getArticleId());
                break;
        }

        try {
            dao.removePermission(entry, folder, account, group, access.isCanRead(), access.isCanWrite());
        } catch (DAOException e) {
            Logger.error(e);
            throw new ControllerException(e);
        }
    }

    public boolean accountHasReadPermission(Account account, Entry entry) throws ControllerException {
        try {
            return dao.hasPermission(entry, null, account, null, true, false);
        } catch (DAOException dao) {
            Logger.error(dao);
        }
        return false;
    }

    public boolean accountHasReadPermission(Account account, Set<Folder> folders) throws ControllerException {
        try {
            return dao.hasPermissionMulti(null, folders, account, null, true, false);
        } catch (DAOException dao) {
            Logger.error(dao);
        }
        return false;
    }

    public boolean accountHasWritePermission(Account account, Set<Folder> folders) throws ControllerException {
        try {
            return dao.hasPermissionMulti(null, folders, account, null, false, true);
        } catch (DAOException dao) {
            Logger.error(dao);
        }
        return false;
    }

    // checks if there is a set permission with write user
    public boolean groupHasWritePermission(Set<Group> groups, Set<Folder> folders) throws ControllerException {
        if (groups.isEmpty())
            return false;

        return dao.hasPermissionMulti(null, folders, null, groups, false, true);
    }

    public boolean isPubliclyVisible(Entry entry) {
        Group publicGroup = groupController.createOrRetrievePublicGroup();
        Set<Group> groups = new HashSet<>();
        groups.add(publicGroup);
        return dao.hasPermissionMulti(entry, null, null, groups, true, false);
    }

    public boolean isPublicVisible(Folder folder) throws ControllerException {
        Group publicGroup = groupController.createOrRetrievePublicGroup();
        Set<Group> groups = new HashSet<>();
        groups.add(publicGroup);
        Set<Folder> folders = new HashSet<>();
        folders.add(folder);
        return groupHasReadPermission(groups, folders);
    }

    public boolean groupHasReadPermission(Set<Group> groups, Set<Folder> folders) throws ControllerException {
        if (groups.isEmpty() || folders.isEmpty())
            return false;

        try {
            return dao.hasPermissionMulti(null, folders, null, groups, true, false);
        } catch (DAOException e) {
            throw new ControllerException(e);
        }
    }

    public boolean hasWritePermission(String userId, Folder folder) {
        if (accountController.isAdministrator(userId) || folder.getOwnerEmail().equalsIgnoreCase(userId))
            return true;

        Account account = accountController.getByEmail(userId);
        return dao.hasSetWriteFolderPermission(folder, account);
    }

    public boolean enablePublicReadAccess(String userId, long partId) throws ControllerException {
        AccessPermission permission = new AccessPermission();
        permission.setType(AccessPermission.Type.READ_ENTRY);
        permission.setTypeId(partId);
        permission.setArticle(AccessPermission.Article.GROUP);
        permission.setArticleId(groupController.createOrRetrievePublicGroup().getId());
        return addPermission(userId, permission) != null;
    }

    public boolean disablePublicReadAccess(String userId, long partId) {
        try {
            AccessPermission permission = new AccessPermission();
            permission.setType(AccessPermission.Type.READ_ENTRY);
            permission.setTypeId(partId);
            permission.setArticle(AccessPermission.Article.GROUP);
            permission.setArticleId(groupController.createOrRetrievePublicGroup().getId());
            removePermission(userId, permission);
            return true;
        } catch (Exception ce) {
            return false;
        }
    }

//    public boolean enableOrDisableFolderPublicAccess(Account account, long folderId, boolean isEnable)
//            throws ControllerException {
//        Folder folder = folderDAO.get(folderId);
//        if (folder == null)
//            return false;
//
//        if (!hasWritePermission(account, folder))
//            throw new ControllerException(account.getEmail() + " cannot modify folder " + folder.getId());
//
//        // propagate permissions
//        if (folder.isPropagatePermissions()) {
//            for (Entry folderContent : folder.getContents()) {
//                if (isEnable)
//                    enablePublicReadAccess(account, folderContent.getId());
//                else
//                    disablePublicReadAccess(account, folderContent.getId());
//            }
//        }
//
//        AccessPermission access = new AccessPermission();
//        access.setArticle(AccessPermission.Article.GROUP);
//        access.setArticleId(groupController.createOrRetrievePublicGroup().getId());
//        access.setType(AccessPermission.Type.READ_FOLDER);
//        access.setTypeId(folderId);
//        if (isEnable)
//            return addPermission(access, null, folder) != null;
//        removePermission(access, null, folder);
//        return true;
//    }

    public Set<Folder> retrievePermissionFolders(Account account) {
        Set<Group> groups = groupController.getAllGroups(account);
        return dao.retrieveFolderPermissions(account, groups);
    }

    /**
     * retrieves permissions that have been explicitly set for entry with the
     * exception of the public group read access. The check for that is a separate
     * method call
     *
     * @param entry entry whose permissions are being checked
     * @return list of set permissions
     */
    public ArrayList<AccessPermission> retrieveSetEntryPermissions(Entry entry) {
        ArrayList<AccessPermission> accessPermissions = new ArrayList<>();
        Set<Permission> permissions = dao.getEntryPermissions(entry);

        Group publicGroup = groupController.createOrRetrievePublicGroup();
        for (Permission permission : permissions) {
            if (permission.getGroup() != null && permission.getGroup() == publicGroup)
                continue;
            accessPermissions.add(permission.toDataTransferObject());
        }

        return accessPermissions;
    }

    /**
     * retrieves permissions that have been explicitly set for a specified folder with the
     * exception of the public group read access. The check for that is a separate
     * method call
     *
     * @param folderId unique identifier for folder whose permissions are being checked
     * @return list of set permissions
     */
    public ArrayList<AccessPermission> getSetFolderPermissions(String userId, long folderId) {
        Folder folder = DAOFactory.getFolderDAO().get(folderId);
        if (folder == null)
            return null;

        FolderAuthorization folderAuthorization = new FolderAuthorization();
        if (!folderAuthorization.canWrite(userId, folder))
            throw new AuthorizationException("User does not have permission to access folder permissions");

        ArrayList<AccessPermission> accessPermissions = new ArrayList<>();
        Set<Permission> permissions = dao.getFolderPermissions(folder);

        for (Permission permission : permissions) {
            accessPermissions.add(permission.toDataTransferObject());
        }

        return accessPermissions;
    }

    /**
     * Retrieves permissions that have been explicitly set for the folders with the exception
     * of the public read permission if specified in the parameter. The call for that is a separate method
     *
     * @param folder        folder whose permissions are being retrieved
     * @param includePublic whether to include public access if set
     * @return list of permissions that have been found for the specified folder
     */
    public ArrayList<AccessPermission> retrieveSetFolderPermission(Folder folder, boolean includePublic) {
        ArrayList<AccessPermission> accessPermissions = new ArrayList<>();

        // read accounts
        Set<Account> readAccounts = dao.retrieveAccountPermissions(folder, false, true);
        for (Account readAccount : readAccounts) {
            accessPermissions.add(new AccessPermission(AccessPermission.Article.ACCOUNT, readAccount.getId(),
                                                       AccessPermission.Type.READ_FOLDER, folder.getId(),
                                                       readAccount.getFullName()));
        }

        // write accounts
        Set<Account> writeAccounts = dao.retrieveAccountPermissions(folder, true, false);
        for (Account writeAccount : writeAccounts) {
            accessPermissions.add(new AccessPermission(AccessPermission.Article.ACCOUNT, writeAccount.getId(),
                                                       AccessPermission.Type.WRITE_FOLDER, folder.getId(),
                                                       writeAccount.getFullName()));
        }

        // read groups
        Set<Group> readGroups = dao.retrieveGroupPermissions(folder, false, true);
        for (Group group : readGroups) {
            if (!includePublic && group.getUuid().equalsIgnoreCase(GroupController.PUBLIC_GROUP_UUID))
                continue;
            accessPermissions.add(new AccessPermission(AccessPermission.Article.GROUP, group.getId(),
                                                       AccessPermission.Type.READ_FOLDER, folder.getId(),
                                                       group.getLabel()));
        }

        // write groups
        Set<Group> writeGroups = dao.retrieveGroupPermissions(folder, true, false);
        for (Group group : writeGroups) {
            accessPermissions.add(new AccessPermission(AccessPermission.Article.GROUP, group.getId(),
                                                       AccessPermission.Type.WRITE_FOLDER, folder.getId(),
                                                       group.getLabel()));
        }

        return accessPermissions;
    }

    public ArrayList<AccessPermission> getDefaultPermissions(Account account) {
        ArrayList<AccessPermission> accessPermissions = new ArrayList<>();
        for (Group group : new GroupController().getAllPublicGroupsForAccount(account)) {
            AccessPermission accessPermission = new AccessPermission();
            accessPermission.setType(AccessPermission.Type.READ_ENTRY);
            accessPermission.setArticle(AccessPermission.Article.GROUP);
            accessPermission.setArticleId(group.getId());
            accessPermission.setDisplay(group.getLabel());
            accessPermissions.add(accessPermission);
        }

        return accessPermissions;
    }

    public boolean propagateFolderPermissions(Account account, Folder folder, boolean prop) throws ControllerException {
        if (!accountController.isAdministrator(account) && !account.getEmail().equalsIgnoreCase(folder.getOwnerEmail()))
            return false;

        // retrieve folder permissions
        ArrayList<AccessPermission> permissions = retrieveSetFolderPermission(folder, true);
        if (permissions.isEmpty())
            return true;
//        boolean isPublic = get

        // if propagate, add permissions to entries contained in here  //TODO : inefficient for large entries/perms
        if (prop) {
            for (Entry entry : folder.getContents()) {
                for (AccessPermission accessPermission : permissions) {
                    addPermission(accessPermission, entry, null);
                }
            }
        } else {
            // else remove permissions
            for (Entry entry : folder.getContents()) {
                for (AccessPermission accessPermission : permissions) {
                    removePermission(accessPermission, entry, null);
                }
            }
        }
        return true;
    }

    /**
     * Sets permissions for the part identified by the part id.
     * If the part does not exist, then a new one is created and permissions assigned. This
     * method clears all existing permissions and sets the specified ones
     *
     * @param userId      unique identifier for user creating permissions. Must have write privileges on the entry
     *                    if one exists
     * @param partId      unique identifier for the part. If a part with this identifier is not located,
     *                    then one is created
     * @param permissions list of permissions to set for the part. Null or empy list will clear all permissions
     * @return part whose permissions have been set. At a minimum it contains the unique identifier for the part
     */
    public PartData setEntryPermissions(String userId, long partId, ArrayList<AccessPermission> permissions) {
        Entry entry = DAOFactory.getEntryDAO().get(partId);
        Account account = DAOFactory.getAccountDAO().getByEmail(userId);
        EntryType type = EntryType.nameToType(entry.getRecordType());
        PartData data = new PartData(type);

        // TODO :
        if (entry == null) {
            partId = new EntryCreator().createPart(userId, data);
            entry = DAOFactory.getEntryDAO().get(partId);
        } else {
            EntryAuthorization authorization = new EntryAuthorization();
            authorization.expectWrite(userId, entry);
            dao.clearPermissions(entry);
        }

        data.setId(partId);

        if (permissions == null)
            return data;

        for (AccessPermission access : permissions) {
            Permission permission = new Permission();
            permission.setEntry(entry);
            entry.getPermissions().add(permission);
            permission.setAccount(account);
            permission.setCanRead(access.isCanRead());
            permission.setCanWrite(access.isCanWrite());
            dao.create(permission);
        }

        return data;
    }

    /**
     * Adds a new permission to the specified entry. If the entry does not exist, a new one is
     * created
     *
     * @param userId unique identifier for user creating the permission. Must have write privileges on the entry
     *               if one exists
     * @param partId unique identifier for the part. If a part with this identifier is not located,
     *               then one is created
     * @param access permissions to be added to the entry
     * @return created permission if successful, null otherwise
     */
    public AccessPermission createPermission(String userId, long partId, AccessPermission access) {
        Entry entry = DAOFactory.getEntryDAO().get(partId);
        EntryType type = EntryType.nameToType(entry.getRecordType());
        PartData data = new PartData(type);

        if (entry == null) {
            partId = new EntryCreator().createPart(userId, data);
            entry = DAOFactory.getEntryDAO().get(partId);
        } else {
            EntryAuthorization authorization = new EntryAuthorization();
            authorization.expectWrite(userId, entry);
        }

        data.setId(partId);

        if (access == null)
            return null;

        Permission permission = new Permission();
        permission.setEntry(entry);
        entry.getPermissions().add(permission);
        Account account = DAOFactory.getAccountDAO().get(access.getArticleId());
        permission.setAccount(account);
        permission.setCanRead(access.isCanRead());
        permission.setCanWrite(access.isCanWrite());
        return dao.create(permission).toDataTransferObject();
    }

    public void removeEntryPermission(String userId, long partId, long permissionId) {
        Entry entry = DAOFactory.getEntryDAO().get(partId);
        if (entry == null)
            return;

        Permission permission = dao.get(permissionId);
        if (permission == null)
            return;

        // expect user to be able to modify entry
        EntryAuthorization authorization = new EntryAuthorization();
        authorization.expectWrite(userId, entry);

        // permission must be for entry and specified entry
        if (permission.getEntry() == null || permission.getEntry().getId() != partId)
            return;

        dao.delete(permission);
    }
}
