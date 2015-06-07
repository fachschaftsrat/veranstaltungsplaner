app = angular.module 'veranstaltungsplaner', []

app.controller 'RegistrationFormCtrl', ['$scope', ($scope) ->

	$scope.restoreState = () ->
		if sessionStorage.registrationForm then $scope.form = angular.fromJson sessionStorage.registrationForm
	
	$scope.saveState = () ->
		sessionStorage.registrationForm = angular.toJson $scope.form

	$scope.restoreState()
]

app.directive 'equals', () ->
	return {
		restrict: 'A',
		require: '?ngModel',
		link: (scope, elem, attrs, ngModel) ->
			if !ngModel then return

			scope.$watch attrs.ngModel, () ->
				validate();

			attrs.$observe 'equals', (val) ->
				validate();

			validate = () ->
				val1 = ngModel.$viewValue
				val2 = attrs.equals

				ngModel.$setValidity 'equals', (val1 == val2)
	}
