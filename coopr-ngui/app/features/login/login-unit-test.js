'use strict';

describe('login feature', function(){
  beforeEach(module('coopr-ngui.features'));

  describe('LoginCtrl', function() {
    var $scope;

    beforeEach(inject(function($rootScope, $controller) {
      $scope = $rootScope.$new();
      $controller('LoginCtrl', {$scope: $scope});
    }));

    it('should init credentials', function() {
      expect($scope.credentials).toBeDefined();
    });

    it('has a doLogin method', function() {
      expect($scope.doLogin).toEqual(jasmine.any(Function));
    });

  });


});
