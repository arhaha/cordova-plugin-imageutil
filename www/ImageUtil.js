var exec = require('cordova/exec');

exports.getImages = function (successCallback, errorCallback, imagePathsStr, maxWidthOrHeight, maxCompressQuality, maxImageSize, maxUnCompressSize, autoCorp) {
    exec(successCallback, errorCallback, 'ImageUtil', 'getImages', [ imagePathsStr, maxWidthOrHeight, maxCompressQuality, maxImageSize, maxUnCompressSize, autoCorp]);
};
