/**
 * Created by leequanfu on 14-6-25.
 */
'use strict';

biocodeApp.filter('range', function () {
    return function (input, total) {
        total = parseInt(total);
        for (var i = 0; i < total; i++)
            input.push(i);
        return input;
    };
});