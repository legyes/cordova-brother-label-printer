cordova.define("com.threescreens.cordova.plugin.brotherPrinter.BrotherPrinter", function(require, exports, module) {
var BrotherPrinter = function () {}
BrotherPrinter.prototype = {
    findNetworkPrinters: function (data, callback, scope) {
        var callbackFn = function () {
            var args = typeof arguments[0] == 'boolean' ? arguments : arguments[0]
            callback.apply(scope || window, args)
        }
        cordova.exec(callbackFn, null, 'BrotherPrinter', 'findNetworkPrinters', [data])
    },
    printViaSDK: function (data, callback) {
        if (!data || !data.length) {
            console.log('No data passed in. Expects a bitmap.')
            return
        }
        cordova.exec(callback, function(err){console.log('error: '+err)}, 'BrotherPrinter', 'printViaSDK', [data])
    },
    sendSnmpRequest: function (data, callback) {
        cordova.exec(callback, function(err){console.log('error: '+err)}, 'BrotherPrinter', 'sendSnmpRequest', [data])
    },
    sendUSBConfig: function (data, callback) {
        if (!data || !data.length) {
            console.log('No data passed in. Expects print payload string.')
            return
        }
        cordova.exec(callback, function(err){console.log('error: '+err)}, 'BrotherPrinter', 'sendUSBConfig', [data])
    }
}
var plugin = new BrotherPrinter()
module.exports = plugin

});
