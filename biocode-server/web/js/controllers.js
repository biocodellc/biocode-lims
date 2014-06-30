/**
 * Created by frank on 24/06/14.
 */
var projectsUrl = "/BiocodeLims/biocode/projects";
var usersUrl = "/BiocodeLims/biocode/users";
var rolesUrl = "/BiocodeLims/biocode/roles";
var usersPage = "#/users"
var projectMap = null;
var projects = null;

var biocodeControllers = angular.module('biocodeControllers', []);

biocodeControllers.controller('projectListCtrl', ['$scope', '$http',
    function($scope, $http) {
        $('li#users').attr('class', '');
        $('li#projects').attr('class', 'active');
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

            $scope.projects = new Array();
            for(var i = 0; i < data.length; i++) {
                var proj = data[i];
                var parentId = proj.parentProjectId;
                if (parentId == -1) {
                    $scope.projects[i] = proj;
                } else {
                    for (var j = 0; j < i; j++) {
                        if ($scope.projects[j].id == parentId) {
                            $scope.projects.splice(j + 1, 0, proj);
                            break;
                        }
                    }
                }
            }

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

                var trNode = $('.tree tr');
                if (trNode.treegrid('isExpanded')) {
                    trNode.treegrid('collapse');
                    trNode.treegrid('expand');
                } else if (trNode.treegrid('isCollapsed')) {
                    trNode.treegrid('expand');
                    trNode.treegrid('collapse');
                }

                trNode = $('#' + nodeId);
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
        $('li#users').attr('class', '');
        $('li#projects').attr('class', 'active');
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

        $http.get(rolesUrl).success(function (data) {
            $scope.roles = data;
        });

        $scope.onAllCheckBox = function(target) {
            $('td input').prop('checked', target.checked);
        }

        $scope.onDeleteUsers = function() {
            var inputs = $(".checkbox") ;
            for (var i = 0; i < inputs.size(); i++) {
                var input = inputs[i];
                if (input.checked === true) {
                    var username = input.parentNode.parentNode.firstElementChild.firstElementChild.innerHTML;

                    $http.delete(projectsUrl + '/' + $scope.project.id + '/roles/' + username).success(function(){
                        alert('backend has not implement this feature now');
                    });
                }
            }
        }
    }]);

biocodeControllers.controller('userListCtrl', ['$scope', '$http',
    function($scope, $http) {
        $('li#users').attr('class', 'active');
        $('li#projects').attr('class', '');
        $http.get(usersUrl).success(function (data) {
            $scope.users = data;
        });

        $scope.onAllCheckBox = function(target) {
            $('td input').prop('checked', target.checked);
        }

        $scope.onDeleteUser = function() {
            var inputs = $(":checked") ;
            for (var i = 0; i < inputs.size(); i++) {
                var input = inputs[i];
                if (input.id === 'all-project-roles')
                    continue;

                var username = input.parentNode.parentNode.firstElementChild.firstElementChild.innerHTML;

                $http.delete(usersUrl + '/' + username).success(function(){
                    for(var i = 0; i < $scope.users.length; i++) {
                        if($scope.users[i].username === username) {
                            $scope.users.splice(i, 1);
                            break;
                        }
                    }
                });
            }
        }
    }]);

biocodeControllers.controller('userDetailCtrl', ['$scope', '$http', '$routeParams',
    function($scope, $http, $routeParams) {
        $('li#users').attr('class', 'active');
        $('li#projects').attr('class', '');
        $scope.newPass = '';
        $http.get(usersUrl + '/' + $routeParams.userId).success(function (data) {
            $scope.user = data;
            initProjects();
        });

        function initProjects() {
            $http.get(projectsUrl).success(function (data) {
                $scope.projectMap = new Object();
                $scope.roles = new Array();
                var roleTmp = new Object();
                for (var i = 0; i < data.length; i++) {
                    $scope.projectMap[data[i].id] = data[i];
                    data[i].parentRoles = new Array();
                    data[i].roles = new Array();
                    data[i].rolesMap = new Array();
                    data[i].level = 0;
                    data[i].hasChild = 'false';
                    data[i].cls = 'treegrid-' + data[i].id;

                    for (var j = 0; j < data[i].userRoles.entry.length; j++) {
                        if (roleTmp[data[i].userRoles.entry[j].value.id] !== true) {
                            data[i].roles.push(data[i].userRoles.entry[j].value);
                            roleTmp[data[i].userRoles.entry[j].value.id] = true;
                        }

                        data[i].rolesMap[data[i].userRoles.entry[j].value.id] = data[i].userRoles.entry[j].value;

                        if (data[i].userRoles.entry[j].key.username === $scope.user.username) {
                            var role = {projectId : data[i].id, projectName : data[i].name, roleName : data[i].userRoles.entry[j].value.name, description : data[i].userRoles.entry[j].value.description};
                            $scope.roles.push(role);
                        }
                    }

                    if (data[i].parentProjectId == -1) {
                        continue;
                    }

                    var p = $scope.projectMap[data[i].parentProjectId];
                    data[i].parentRoles.push(p.roles);
                    data[i].level = p.level + 1;
                    data[i].cls = data[i].cls + ' treegrid-parent-' + p.id;
                    p.hasChild = 'true';
                    p.cls = p.cls + ' treegrid-expanded'
                }

                $scope.projects = data;
                projects = $scope.projects;
                projectMap = $scope.projectMap;
            });

            $http.get(rolesUrl).success(function (data) {
                $scope.projectRoles = data;
                $scope.projectRolesMap = new Object();

                for (var i = 0; i < data.length; i++) {
                    $scope.projectRolesMap[data[i].id] = data[i];
                }
            });
        }

        $scope.onAllCheckBox = function(target) {
            $('td input').prop('checked', target.checked);
        }

        $scope.onDeleteUser = function() {
            $http.delete(usersUrl + '/' + $scope.user.username).success(function(){
                window.location = usersPage;
            });
        }

        $scope.onUpdateUser = function() {
            $http.put(usersUrl + '/' + $scope.user.username, $scope.user).success(function(){
                $scope.myData.modalShown = !$scope.myData.modalShown;
            });
        }

        $scope.submitPass = function() {
            var tmpUser = $scope.user;
            tmpUser.password = $('#passinput')[0].value;
            $http.put(usersUrl + '/' + $scope.user.username, tmpUser).success(function(){
                alert('update sucessfull');
            });
        }

        $scope.onDeleteRole = function() {
            var inputs = $(".checkbox") ;
            for (var i = 0; i < inputs.size(); i++) {
                var input = inputs[i];
                if (input.checked === true) {
                    var projectId = input.parentNode.parentNode.firstElementChild.firstElementChild.getAttribute('value');

                    var url = projectsUrl + '/' + projectId + '/roles/' + $scope.user.username;
                    $http.delete(url).success(function(){
                        initProjects();
                    });
                }
            }
        }

        $scope.onProjectChange = function(projId) {
            $scope.assignedRoles = $scope.projectMap[projId].roles;
        }

        $scope.onAssignRole = function() {
            var url = projectsUrl + '/' + $scope.projId + '/roles/' + $scope.user.username;
            $http.put(url, $scope.projectRolesMap[$scope.roleId]).success(function (data, status, headers) {
                initProjects();
            });
        }

        $scope.myData = {
            link: "http://google.com",
            modalShown: false,
            hello: 'world',
            foo: 'bar'
        }
        $scope.logClose = function() {
            console.log('close!');
        };
        $scope.toggleModal = function() {
            $scope.myData.modalShown = !$scope.myData.modalShown;
        };
    }]);

biocodeControllers.controller('createuserCtrl', ['$scope', '$http',
    function($scope, $http) {
        $('li#users').attr('class', 'active');
        $('li#projects').attr('class', '');

        $scope.onCreateUser = function() {
            $http.post(usersUrl, $scope.user).success(function (data, status, headers) {
                window.location = usersPage + '/' + $scope.user.username;
            });
        }
    }]);
