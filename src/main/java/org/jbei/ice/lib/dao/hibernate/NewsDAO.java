package org.jbei.ice.lib.dao.hibernate;

import java.util.ArrayList;

import org.jbei.ice.lib.dao.DAOException;
import org.jbei.ice.lib.news.News;

import org.hibernate.HibernateException;
import org.hibernate.Query;
import org.hibernate.Session;

/**
 * DAO for managing {@link org.jbei.ice.lib.news.News}
 *
 * @author Hector Plahar
 */
public class NewsDAO extends HibernateRepository<News> {

    public News get(long id) throws DAOException {
        return super.get(News.class, id);
    }

    @SuppressWarnings("unchecked")
    public ArrayList<News> retrieveAll() throws DAOException {
        Session session = currentSession();
        try {
            ArrayList<News> results = new ArrayList<News>();
            Query query = session.createQuery("from " + News.class.getName() + " order by creationTime DESC ");
            results.addAll(query.list());
            return results;

        } catch (HibernateException e) {
            throw new DAOException("Failed to retrieve news", e);
        }
    }
}