'use strict';

angular.module('ice.entry.directives', [])
    .directive("icePlate96", function () {
        return {
            scope: {
                sample: "=",
                delete: "&onDelete",
                remote: "=",
                entry: "=",
                plate: "=",
                selected: "="
            },

            restrict: "E",
            templateUrl: "scripts/entry/sample/plate96.html",
            controller: "DisplaySampleController"
        }
    })
    .directive("iceShelf", function () {
        return {
            scope: {
                sample: "=",
                delete: "&onDelete",
                remote: "="
            },

            restrict: "E",
            templateUrl: "scripts/entry/sample/shelf.html",
            controller: "DisplaySampleController"
        }
    })
    .directive("iceGeneric", function () {
        return {
            scope: {
                sample: "=",
                delete: "&onDelete",
                remote: "="
            },

            restrict: "E",
            templateUrl: "scripts/entry/sample/generic.html",
            controller: "DisplaySampleController"
        }
    })
    .directive("iceAddgene", function () {
        return {
            scope: {
                sample: "=",
                delete: "&onDelete",
                remote: "="
            },

            restrict: "E",
            templateUrl: "scripts/entry/sample/addgene.html",
            controller: "DisplaySampleController"
        }
    })
    .directive("iceVectorViewer", function () {
        return {
            scope: false,
            restrict: "AE",
            transclude: true,

            link: function (scope, element, attrs) {
                var entryId;
                scope.$watch('entry', function (value) {
                    if (!value) {
                        if (attrs.partId) {
                            entryId = attrs.partId;
                        }
                    } else {
                        entryId = value.id;
                    }

                    if (entryId) {
                        scope.fetchEntrySequence(entryId);
                    }
                });
            },

            template: '<div id="ve-Root"><br><img src="img/loader-mini.gif"> Loading sequence&hellip;</div>',

            controller: function ($scope, Util, $window) {
                $scope.loadVectorEditor = function (data) {
                    $scope.editor = $window.createVectorEditor(document.getElementById("ve-Root"), {
                        onSave: function (event, sequenceData, editorState) {
                            console.log("event:", event);
                            console.log("sequenceData:", sequenceData);
                            console.log("editorState:", editorState);
                        },

                        onCopy: function (event, sequenceData, editorState) {
                            console.log("event:", event);
                            console.log("sequenceData:", sequenceData);
                            console.log("editorState:", editorState);

                            const clipboardData = event.clipboardData || window.clipboardData || event.originalEvent.clipboardData;
                            clipboardData.setData('text/plain', sequenceData.sequence);
                            $scope.data.selection = editorState.selectionLayer;
                            clipboardData.setData('application/json', JSON.stringify($scope.data));
                            event.preventDefault();
                        }
                    });

                    $scope.editor.updateEditor({
                        sequenceData: data.sequenceData,
                        annotationVisibility: {
                            parts: false,
                            orfs: false,
                            cutsites: false,
                        },
                        panelsShown: {
                            sequence: false,
                            circular: true,
                            rail: true
                        }
                    });
                };

                $scope.fetchEntrySequence = function (entryId) {
                    Util.get("rest/parts/" + entryId + "/sequence", function (result) {
                        var data = {
                            sequenceData: {
                                sequence: result.sequence, features: [], name: $scope.entry.name
                            },
                            registryData: {
                                uri: result.uri,
                                identifier: result.identifier,
                                name: result.name,
                                circular: result.circular
                            }
                        };

                        for (var i = 0; i < result.features.length; i += 1) {
                            var feature = result.features[i];
                            if (!feature.locations.length)
                                continue;

                            var location = feature.locations[0];

                            data.sequenceData.features.push({
                                start: location.genbankStart,
                                end: location.end,
                                id: feature.id,
                                forward: feature.strand == 1,
                                type: feature.type,
                                name: feature.name,
                                notes: feature.notes,
                                annotationType: feature.type
                            });
                        }

                        $scope.loadVectorEditor(data);
                    });
                };
            }
        };
    });
