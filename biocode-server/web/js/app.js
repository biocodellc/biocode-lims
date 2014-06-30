/**
 * Created by frank on 24/06/14.
 */
'use strict';

/* App Module */

var biocodeApp = angular.module('biocodeApp', [
    'ngRoute',
    'biocodeControllers'
]);

biocodeApp.config(['$routeProvider',
    function($routeProvider) {
        $routeProvider.
            when('/projects', {
                templateUrl: 'partials/project-list.html',
                controller: 'projectListCtrl'
            }).
            when('/projects/:projectId', {
                templateUrl: 'partials/project-detail.html',
                controller: 'projectDetailCtrl'
            }).
            when('/users', {
                templateUrl: 'partials/user-list.html',
                controller: 'userListCtrl'
            }).
            when('/users/:userId', {
                templateUrl: 'partials/user-detail.html',
                controller: 'userDetailCtrl'
            }).
            when('/create-user', {
                templateUrl: 'partials/create-user.html',
                controller: 'createuserCtrl'
            }).
            when('/about', {
                templateUrl: 'partials/about.html'
            }).
            when('/contact', {
                templateUrl: 'partials/contact.html'
            }).
            otherwise({
                redirectTo: '/projects'
            });
    }]);
