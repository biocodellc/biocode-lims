/**
 * Created by frank on 24/06/14.
 */
var projectsUrl = "data/projects.json";
var usersUrl = "data/users.json";
var projectMap = null;
var projects = null;

var biocodeControllers = angular.module('biocodeControllers', []);

biocodeControllers.controller('projectListCtrl', ['$scope', '$http',
    function($scope, $http) {
        $http.get(projectsUrl).success(function (data) {
            $scope.projectMap = new Object();
            for (var i = 0; i < data.length; i++) {
                $scope.projectMap[data[i].id] = data[i];
                data[i].parentRoles = new Array();
                data[i].level = 0;
                data[i].hasChild = 'false';
                data[i].cls = 'treegrid-' + data[i].id;

                if (data[i].parentProjectId == -1) {
                    continue;
                }

                var p = $scope.projectMap[data[i].parentProjectId];
                data[i].level = p.level + 1;
                data[i].cls = data[i].cls + ' treegrid-parent-' + p.id;
                p.hasChild = 'true';
                p.cls = p.cls + ' treegrid-expanded'
            }

            $scope.projects = data;
            projects = $scope.projects;
            projectMap = $scope.projectMap;
        });

        $scope.isFirst = true;
        $scope.collapseOrExpend = function(target) {
            if ($scope.isFirst) {
                var nodeId = target.parentNode.parentNode.id;
                $('.tree').treegrid({
                    expanderExpandedClass: 'glyphicon glyphicon-minus',
                    expanderCollapsedClass: 'glyphicon glyphicon-plus'
                });

                var trNode = $('#' + nodeId);
                if (trNode.treegrid('isExpanded'))
                    trNode.treegrid('collapse');
                else if (trNode.treegrid('isCollapsed'))
                    trNode.treegrid('expand');
            }

            $scope.isFirst = false;
        };
    }]);

biocodeControllers.controller('projectDetailCtrl', ['$scope', '$http', '$routeParams',
    function($scope, $http, $routeParams) {
        if (projectMap == null) {
            $http.get(projectsUrl).success(function (data) {
                projectMap = new Object();
                for (var i = 0; i < data.length; i++) {
                    projectMap[data[i].id] = data[i];
                    data[i].parentRoles = new Array();
                    data[i].level = 0;
                    data[i].hasChild = 'false';
                    data[i].cls = 'treegrid-' + data[i].id;

                    if (data[i].parentProjectId == -1) {
                        continue;
                    }

                    var p = projectMap[data[i].parentProjectId];
                    data[i].level = p.level + 1;
                    data[i].cls = data[i].cls + ' treegrid-parent-' + p.id;
                    p.hasChild = 'true';
                    p.cls = p.cls + ' treegrid-expanded'
                }

                projects = data;
                $scope.project = projectMap[$routeParams.projectId];
                $scope.userRoles = $scope.project.userRoles.entry;
            });
        }
    }]);

biocodeControllers.controller('userListCtrl', ['$scope', '$http',
    function($scope, $http) {
    }]);

biocodeControllers.controller('userDetailCtrl', ['$scope', '$routeParams',
    function($scope, $routeParams) {
        $scope.userId = $routeParams.userId;
    }]);
