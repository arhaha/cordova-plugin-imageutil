package com.matrixgz.cordova.plugin;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Environment;

import org.apache.cordova.LOG;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;

import java.text.SimpleDateFormat;

import java.util.Date;

/**
 * This class echoes a string called from JavaScript.
 */
public class ImageUtil extends CordovaPlugin {

    private static final String JPEG_EXTENSION = ".jpg";
    // private static final String PNG_EXTENSION = ".png";
    // private static final String PNG_MIME_TYPE = "image/png";
    // private static final String JPEG_MIME_TYPE = "image/jpeg";

    private static final String LOG_TAG = "ImageUtil";
    private static final String TIME_FORMAT = "yyyyMMdd_HHmmss";

    private int maxWidthOrHeight = 2048;
    private int maxCompressQuality = 90;
    private int maxImageSize = 5 * 1024 * 1024; // 5mb
    private int maxUnCompressSize = 512 * 1024; // 512kb
    private boolean autoCrop = false;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if (action.equals("getImages")) {
            String pathStr = args.getString(0);
            this.maxWidthOrHeight = args.getInt(1);
            this.maxCompressQuality = args.getInt(2);
            this.maxImageSize = args.getInt(3);
            this.maxUnCompressSize = args.getInt(4);
            this.autoCrop = args.getBoolean(5);
            this.getImages(callbackContext, pathStr);
            return true;
        }
        return false;
    }

    private void getImages(CallbackContext callbackContext, String pathStr) {
        LOG.d(LOG_TAG,
                "getImages pathStr:" + pathStr + "   maxWidthOrHeight:" + maxWidthOrHeight + "  maxCompressQuality:"
                        + maxCompressQuality + "  maxImageSize:" + maxImageSize / 1024 + "kb" + "  maxUnCompressSize:"
                        + maxUnCompressSize / 1024 + "kb  autoCrop:" + autoCrop);
        String[] paths = pathStr.split(",");
        JSONArray ja = new JSONArray();
        for (int i = 0; i < paths.length; ++i) {
            try {
                String result = this.handleFile(paths[i]);
                if (result != null)
                    ja.put(result);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        callbackContext.success(ja);
    }

    private String handleFile(String sourcePath) throws IOException {
        ExifHelper exif = new ExifHelper();
        try {
            // We don't support PNG, so let's not pretend we do
            exif.createInFile(FileHelper.stripFileProtocol(sourcePath));
            exif.readExifData();

        } catch (IOException e) {
            e.printStackTrace();
        }

        Uri uri = Uri.fromFile(createCaptureFile(System.currentTimeMillis() + ""));
        Bitmap bitmap = getScaledAndRotatedBitmap(sourcePath);

        // Double-check the bitmap.
        if (bitmap == null) {
            LOG.d(LOG_TAG, "I either have a null image path or bitmap");
            return null;
        }

        bitmap = reSetImgSize(bitmap, this.maxWidthOrHeight);

        // Add compressed version of captured image to returned media store Uri
        OutputStream os = this.cordova.getActivity().getContentResolver().openOutputStream(uri);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(CompressFormat.JPEG, 100, baos);// 质量压缩方法，这里100表示不压缩，
        int sourceLength = baos.toByteArray().length;
        if (sourceLength > this.maxImageSize) {
            compressImage(bitmap, this.maxCompressQuality, this.maxImageSize, CompressFormat.JPEG, baos);
        } else if (sourceLength > this.maxUnCompressSize) {
            compressImage(bitmap, this.maxCompressQuality, sourceLength, CompressFormat.JPEG, baos);
        }
        LOG.d(LOG_TAG, "sourcePath:" + sourcePath + "   sourceLength:" + sourceLength / 1024 + "kb  compressLength:"
                + baos.toByteArray().length / 1024 + "kb");
        os.write(baos.toByteArray());
        os.close();
        baos.close();

        // Restore exif data to file
        String exifPath;
        exifPath = uri.getPath();
        // We just finished rotating it by an arbitrary orientation, just make sure it's
        // normal
        exif.createOutFile(exifPath);
        exif.writeExifData();

        if (bitmap != null) {
            bitmap.recycle();
            bitmap = null;
        }
        System.gc();

        // Send Uri back to JavaScript for viewing image
        return uri.toString();
    }

    private void compressImage(Bitmap image, int quality, int reqSize, CompressFormat compressFormat,
            ByteArrayOutputStream baos) {
        do {
            baos.reset();// 清空baos
            image.compress(compressFormat, quality, baos);// 这里压缩options%，把压缩后的数据放到baos中
            LOG.d(LOG_TAG, "compressImage  quality:" + quality + " length:" + baos.toByteArray().length / 1024 + "kb");
            quality -= 5;
        } while (quality > 0 && baos.toByteArray().length > reqSize);
    }

    public Bitmap reSetImgSize(Bitmap bm, int maxSize) {
        // 获得图片的宽高.
        int width = bm.getWidth();
        int height = bm.getHeight();

        float scale = 0;

        if (width >= height && width > maxSize) {
            scale = ((float) maxSize) / width;
        } else if (height > width && height > maxSize) {
            scale = ((float) maxSize) / height;
        } else {
            return bm;
        }
        // 取得想要缩放的matrix参数.
        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);
        // 得到新的图片.
        Bitmap newbm = Bitmap.createBitmap(bm, 0, 0, width, height, matrix, true);
        return newbm;
    }

    private File createCaptureFile(String fileName) {
        if (fileName.isEmpty()) {
            fileName = ".Pic";
        }

        fileName = fileName + JPEG_EXTENSION;

        return new File(getTempDirectoryPath(), fileName);
    }

    private String getTempDirectoryPath() {
        File cache = null;

        // SD Card Mounted
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            cache = cordova.getActivity().getExternalCacheDir();
        }
        // Use internal storage
        else {
            cache = cordova.getActivity().getCacheDir();
        }

        // Create the cache directory if it doesn't exist
        cache.mkdirs();
        return cache.getAbsolutePath();
    }

    private Bitmap getScaledAndRotatedBitmap(String imageUrl) throws IOException {
        InputStream fileStream = null;
        Bitmap image = null;
        try {
            fileStream = FileHelper.getInputStreamFromUriString(imageUrl, cordova);
            image = BitmapFactory.decodeStream(fileStream);
        } catch (OutOfMemoryError e) {
            LOG.e(LOG_TAG, e.getLocalizedMessage());
            return null;
        } catch (Exception e) {
            LOG.e(LOG_TAG, e.getLocalizedMessage());
            return null;
        } finally {
            if (fileStream != null) {
                try {
                    fileStream.close();
                } catch (IOException e) {
                    LOG.d(LOG_TAG, "Exception while closing file input stream.");
                }
            }
        }
        return image;
    }

    private String outputModifiedBitmap(Bitmap bitmap, Uri uri, ExifHelper exifData) throws IOException {
        // Some content: URIs do not map to file paths (e.g. picasa).
        String realPath = FileHelper.getRealPath(uri, this.cordova);

        // Get filename from uri
        String fileName = realPath != null ? realPath.substring(realPath.lastIndexOf('/') + 1) : ("modified.jpg");

        String timeStamp = new SimpleDateFormat(TIME_FORMAT).format(new Date());
        // String fileName = "IMG_" + timeStamp + (this.encodingType == JPEG ? ".jpg" :
        // ".png");
        String modifiedPath = getTempDirectoryPath() + "/" + fileName;

        OutputStream os = new FileOutputStream(modifiedPath);
        CompressFormat compressFormat = CompressFormat.JPEG;

        bitmap.compress(compressFormat, 85, os);
        os.close();

        try {
            exifData.createOutFile(modifiedPath);
            exifData.writeExifData();
            exifData = null;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return modifiedPath;
    }
}
