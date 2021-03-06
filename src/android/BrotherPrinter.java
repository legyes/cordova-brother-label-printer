package com.threescreens.cordova.plugin.brotherPrinter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Picture;
import android.graphics.Typeface;
import android.os.Handler;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.app.PendingIntent;

import com.brother.ptouch.sdk.LabelInfo;
import com.brother.ptouch.sdk.NetPrinter;
import com.brother.ptouch.sdk.Printer;
import com.brother.ptouch.sdk.PrinterInfo;
import com.brother.ptouch.sdk.PrinterStatus;

import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.os.Bundle;

import static android.graphics.Color.BLACK;

import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.TransportMapping;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.Address;
import org.snmp4j.smi.Integer32;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultUdpTransportMapping;

public class BrotherPrinter extends CordovaPlugin {

    String modelName = "QL-810W";
    int copyCount = 1;
    boolean autoCut = true;
    boolean endCut = true;
    String labelType = "normal";
    String paperType = "W62H29";
    int printQuality = 0;
    Bitmap printBitmap;

    private NetPrinter[] netPrinters;

    private String ipAddress = null;
    private String macAddress = null;
    private Boolean searched = false;
    private Boolean found = false;

    //token to make it easy to grep logcat
    private static final String TAG = "BROTHERPRINTER";

    private CallbackContext callbackctx;

    public static Snmp snmp;
    public static CommunityTarget comtarget;
    static PDU pdu;
    static OID oid;
    static VariableBinding req;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {

        if ("findNetworkPrinters".equals(action)) {
            try {
                findNetworkPrinters(args, callbackContext);
            } catch (Exception e) {
                final PluginResult result;
                result = new PluginResult(PluginResult.Status.OK, "EXCEPTION");
                callbackctx.sendPluginResult(result);
            }
            return true;
        }

        if ("printViaSDK".equals(action)) {
            try {
                printViaSDK(args, callbackContext);
            } catch (Exception e) {
                final PluginResult result;
                result = new PluginResult(PluginResult.Status.OK, "EXCEPTION");
                callbackctx.sendPluginResult(result);
            }
            return true;
        }

        if ("sendSnmpRequest".equals(action)) {
            try {
                sendSnmpRequest(args, callbackContext);
            } catch (Exception e) {
                final PluginResult result;
                result = new PluginResult(PluginResult.Status.OK, "EXCEPTION");
                callbackctx.sendPluginResult(result);
            }
            return true;
        }

        if ("sendUSBConfig".equals(action)) {
            try {
                sendUSBConfig(args, callbackContext);
            } catch (Exception e) {
                final PluginResult result;
                result = new PluginResult(PluginResult.Status.OK, "EXCEPTION");
                callbackctx.sendPluginResult(result);
            }

            return true;
        }

        return false;
    }

    private NetPrinter[] enumerateNetPrinters(String printerModelName) {
        Printer myPrinter = new Printer();
        PrinterInfo myPrinterInfo = new PrinterInfo();
        netPrinters = myPrinter.getNetPrinters(printerModelName);
        return netPrinters;
    }

    private void findNetworkPrinters(final JSONArray args, final CallbackContext callbackctx) throws Exception  {
        try {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    try {

                        searched = true;

                        JSONObject j = new JSONObject();

                        try {
                            j = new JSONObject(args.optJSONArray(0).optString(0));
                        } catch (JSONException e) {
                            final PluginResult result;
                            result = new PluginResult(PluginResult.Status.ERROR, "JSON error");
                            callbackctx.sendPluginResult(result);
                        }

                        modelName = j.optString("modelName").toString();

                        NetPrinter[] netPrinters = enumerateNetPrinters(modelName);
                        int netPrinterCount = netPrinters.length;

                        JSONArray jArray = new JSONArray();
                        JSONObject jItem = new JSONObject();

                        if (netPrinterCount > 0) {
                            found = true;
                            Log.d(TAG, "---- network printers found! ----");

                            for (int i = 0; i < netPrinterCount; i++) {
                                ipAddress = netPrinters[i].ipAddress;
                                macAddress = netPrinters[i].macAddress;

                                jArray = new JSONArray();
                                jItem = new JSONObject();

                                jItem.put("index", Integer.toString(i));
                                jItem.put("modelName", netPrinters[i].modelName);
                                jItem.put("ipAddress", netPrinters[i].ipAddress);
                                jItem.put("macAddress", netPrinters[i].macAddress);
                                jItem.put("serNo", netPrinters[i].serNo);
                                jItem.put("nodeName", netPrinters[i].nodeName);

                                jArray.put(jItem);

                                Log.d(TAG,
                                    " idx:    " + Integer.toString(i) +
                                    "\n model:  " + netPrinters[i].modelName +
                                    "\n ip:     " + netPrinters[i].ipAddress +
                                    "\n mac:    " + netPrinters[i].macAddress +
                                    "\n serial: " + netPrinters[i].serNo +
                                    "\n name:   " + netPrinters[i].nodeName
                                );
                            }

                            Log.d(TAG, "---- /network printers found! ----");

                        } else if (netPrinterCount == 0) {
                            found = false;
                            Log.d(TAG, "!!!! No network printers found !!!!");
                        }

                        JSONArray args = new JSONArray();
                        PluginResult result;

                        Boolean available = netPrinterCount > 0;

                        args.put(available);
                        args.put(jArray);

                        result = new PluginResult(PluginResult.Status.OK, args);

                        callbackctx.sendPluginResult(result);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }

            });
        } catch (Exception e) {
            // TODO
        }
    }

    public static Bitmap bmpFromBase64(String base64, final CallbackContext callbackctx) {
        try {
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public Bitmap textAsBitmap(String text, String text1, String text2, String text3, String text4) {
        Paint paint = new Paint();
        paint.setTextSize(80);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.LEFT);
        float baseline = -paint.ascent(); // ascent() is negative
        /*
        int width = (int) (paint.measureText("qwertzuiopasdfgqwertzuiopasdfg") + 0.5f); // round
        int height = (int) (baseline + paint.descent() + 0.5f);
        Bitmap image = Bitmap.createBitmap(width + 500, height + 450, Bitmap.Config.ARGB_8888);
        */

        int width = 1320;
        int height = 495;
        Bitmap image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(image);
        //canvas.drawRect(0, 0, width + 500, height + 450, paint);
        canvas.drawRect(0, 0, width, height, paint);

        paint.setColor(Color.BLACK);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText(text, 0, baseline, paint);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        canvas.drawText(text1, 0, baseline + 100, paint);
        canvas.drawText(text2, 0, baseline + 200, paint);
        canvas.drawText(text3, 0, baseline + 300, paint);
        canvas.drawText(text4, 0, baseline + 400, paint);
        return image;
    }

    public Bitmap labelTypeNormalBitmap(String row1Title, String row2Title, String row2Text, String row3Title, String row3Text) {
        Paint paint = new Paint();
        paint.setTextSize(80);
        paint.setColor(Color.WHITE);
        //paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.LEFT);

        float baseline = -paint.ascent();
        int width = 1320;
        int height = 290;
        Bitmap image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(image);

        canvas.drawRect(0, 0, width, height, paint);

        paint.setColor(Color.BLACK);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        canvas.drawText(row1Title, 0, baseline, paint);

        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));

        canvas.drawText(row2Title, 0, baseline + 100, paint);
        canvas.drawText(row2Text, 500, baseline + 100, paint);

        canvas.drawText(row3Title, 0, baseline + 200, paint);

        paint.setUnderlineText(true);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText(row3Text, 500, baseline + 200, paint);

        /*
        paint.setStrokeWidth(10);
        paint.setColor(Color.BLACK);
        paint.setStyle(Paint.Style.STROKE);
        // BORDER
        canvas.drawRect(0, 0, width, height, paint);		
        */
        return image;
    }

    public Bitmap labelTypeFrozenBitmap(String row1Title, String row2Title, String row2Text, String row3Title, String row3Text, String row4Title, String row4Text) {
        Paint paint = new Paint();
        paint.setTextSize(80);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.LEFT);
        float baseline = -paint.ascent();

        int width = 1320;
        int height = 400;
        Bitmap image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(image);

        canvas.drawRect(0, 0, width, height, paint);

        paint.setColor(Color.BLACK);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        canvas.drawText(row1Title, 0, baseline, paint);

        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));

        canvas.drawText(row2Title, 0, baseline + 100, paint);
        canvas.drawText(row2Text, 500, baseline + 100, paint);

        canvas.drawText(row3Title, 0, baseline + 200, paint);

        paint.setUnderlineText(false);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        canvas.drawText(row3Text, 500, baseline + 200, paint);

        canvas.drawText(row4Title, 0, baseline + 300, paint);
        paint.setUnderlineText(true);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText(row4Text, 500, baseline + 300, paint);

        return image;
    }

    public Bitmap labelTypeMeatBitmap(String row1Title, String row2Title, String row2Text, String row3Title, String row3Text, String row4Text, String row5Text) {
        Paint paint = new Paint();
        paint.setTextSize(80);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.LEFT);
        float baseline = -paint.ascent();

        int width = 1320;
        int height = 505;
        Bitmap image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(image);

        canvas.drawRect(0, 0, width, height, paint);

        paint.setColor(Color.BLACK);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        canvas.drawText(row1Title, 0, baseline, paint);

        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));

        canvas.drawText(row2Title, 0, baseline + 100, paint);
        canvas.drawText(row2Text, 500, baseline + 100, paint);

        canvas.drawText(row3Title, 0, baseline + 200, paint);

        paint.setUnderlineText(true);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText(row3Text, 500, baseline + 200, paint);

        paint.setUnderlineText(false);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        canvas.drawText("_____ " + row4Text + "    ________________________", 0, baseline + 360, paint);

        paint.setTextSize(60);
        canvas.drawText(row5Text, 700, baseline + 430, paint);

        return image;
    }

    private void printViaSDK(final JSONArray args, final CallbackContext callbackctx) throws Exception  {
        try {

            JSONObject j = new JSONObject();

            try {
                j = new JSONObject(args.optJSONArray(0).optString(0));
            } catch (JSONException e) {
                final PluginResult result;
                result = new PluginResult(PluginResult.Status.ERROR, "JSON error");
                callbackctx.sendPluginResult(result);
            }

            copyCount = Integer.parseInt(j.optString("copy").toString());
            autoCut = Boolean.parseBoolean(j.optString("autoCut").toString());
            endCut = Boolean.parseBoolean(j.optString("endCut").toString());
            labelType = j.optString("labelType").toString();
            paperType = j.optString("paperType").toString();
            printQuality = Integer.parseInt(j.optString("printQuality").toString());

            if (labelType.equalsIgnoreCase("meat")) {
                printBitmap = labelTypeMeatBitmap(
                    j.optString("text1"),
                    j.optString("text2"),
                    j.optString("text3"),
                    j.optString("text4"),
                    j.optString("text5"),
                    j.optString("text6"),
                    j.optString("text7")
                );
            } else if (labelType.equalsIgnoreCase("frozen")) {
                printBitmap = labelTypeFrozenBitmap(
                    j.optString("text1"),
                    j.optString("text2"),
                    j.optString("text3"),
                    j.optString("text4"),
                    j.optString("text5"),
                    j.optString("text6"),
                    j.optString("text7")
                );
            } else if (labelType.equalsIgnoreCase("custom")) {
                printBitmap = textAsBitmap(
                    j.optString("text1"),
                    j.optString("text2"),
                    j.optString("text3"),
                    j.optString("text4"),
                    j.optString("text5")
                );
            } else {
                printBitmap = labelTypeNormalBitmap(
                    j.optString("text1"),
                    j.optString("text2"),
                    j.optString("text3"),
                    j.optString("text4"),
                    j.optString("text5")
                );
            }

            final Bitmap bitmap = printBitmap;

            if (!searched) {
                final PluginResult result;
                result = new PluginResult(PluginResult.Status.ERROR, "You must first run findNetworkPrinters() to search the network.");
                callbackctx.sendPluginResult(result);
            }

            if (!found) {
                final PluginResult result;
                result = new PluginResult(PluginResult.Status.ERROR, "No printer was found. Aborting.");
                callbackctx.sendPluginResult(result);
            }

            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    try {

                        Printer myPrinter = new Printer();
                        PrinterInfo myPrinterInfo = new PrinterInfo();

                        myPrinterInfo = myPrinter.getPrinterInfo();

                        Log.d(TAG, myPrinterInfo.toString());

                        // https://mariusbelin.files.wordpress.com/2017/02/brother-print-sdk-for-android-manual.pdf
                        // PrinterInfo.Halftone.PATTERNDITHER / ERRORDIFFUSION / THRESHOLD
                        // PrinterInfo.Align.KEFT / RIGHT / CENTER
                        // PrinterInfo.VAlign.TOP / MIDDLE / BOTTOM
                        // PrinterInfo.Margin = int

                        if (modelName.equals("QL-820NWB")) {
                            myPrinterInfo.printerModel = PrinterInfo.Model.QL_820NWB;
                        } else {
                            myPrinterInfo.printerModel = PrinterInfo.Model.QL_810W;
                        }

                        //myPrinterInfo.printerModel = PrinterInfo.Model.QL_820NWB;
                        myPrinterInfo.port = PrinterInfo.Port.NET;
                        //myPrinterInfo.printMode     = PrinterInfo.PrintMode.ORIGINAL;
                        myPrinterInfo.orientation = PrinterInfo.Orientation.PORTRAIT;
                        myPrinterInfo.printMode = PrinterInfo.PrintMode.FIT_TO_PAGE;
                        myPrinterInfo.halftone = PrinterInfo.Halftone.PATTERNDITHER;
                        //myPrinterInfo.trimTapeAfterData = false;
                        //myPrinterInfo.orientation   = PrinterInfo.Orientation.LANDSCAPE;

                        myPrinterInfo.align = PrinterInfo.Align.CENTER;
                        myPrinterInfo.valign = PrinterInfo.VAlign.MIDDLE;

                        /*myPrinterInfo.checkPrintEnd = PrinterInfo.CheckPrintEnd.CPE_NO_CHECK;*/
                        /* myPrinterInfo.thresholdingValue = 220; */

                        switch (printQuality) {
                            case 1:
                                myPrinterInfo.printQuality = PrinterInfo.PrintQuality.DOUBLE_SPEED;
                                break;
                            case 2:
                                myPrinterInfo.printQuality = PrinterInfo.PrintQuality.LOW_RESOLUTION;
                                break;
                            case 3:
                                myPrinterInfo.printQuality = PrinterInfo.PrintQuality.HIGH_RESOLUTION;
                                break;
                            default:
                                myPrinterInfo.printQuality = PrinterInfo.PrintQuality.NORMAL;
                                break;
                        }

                        myPrinterInfo.numberOfCopies = copyCount;

                        /* myPrinterInfo.paperSize = PrinterInfo.PaperSize.CUSTOM;*/
                        myPrinterInfo.ipAddress = ipAddress;
                        myPrinterInfo.macAddress = macAddress;
                        myPrinter.setPrinterInfo(myPrinterInfo);

                        LabelInfo myLabelInfo = new LabelInfo();

                        //myLabelInfo.labelNameIndex  = myPrinter.checkLabelInPrinter();
                        //myLabelInfo.labelNameIndex  = 9; // W62H29
                        myLabelInfo.labelNameIndex = LabelInfo.QL700.valueOf(paperType).ordinal();
                        //myLabelInfo.labelNameIndex = LabelInfo.QL700.valueOf("W62H29").ordinal();
                        //myLabelInfo.labelNameIndex = LabelInfo.QL700.valueOf("W62").ordinal();
                        myLabelInfo.isAutoCut = autoCut;
                        myLabelInfo.isEndCut = endCut;
                        myLabelInfo.isHalfCut = false;
                        myLabelInfo.isSpecialTape = false;


                        //label info must be set after setPrinterInfo, it's not in the docs
                        myPrinter.setLabelInfo(myLabelInfo);

                        String labelWidth = "" + myPrinter.getLabelParam().labelWidth;
                        String paperWidth = "" + myPrinter.getLabelParam().paperWidth;
                        Log.d(TAG, "paperWidth = " + paperWidth);
                        Log.d(TAG, "labelWidth = " + labelWidth);

                        PrinterStatus status = myPrinter.printImage(bitmap);
                        /*
                        Boolean val= myPrinter.startPTTPrint(3, null);
                        myPrinter.replaceText("asd");
						
                        PrinterStatus status=myPrinter.flushPTTPrint();//ERROR thrown here
                        */
                        //casting to string doesn't work, but this does... wtf Brother
                        String status_code = "" + status.errorCode;

                        Log.d(TAG, "PrinterStatus: " + status_code);

                        final PluginResult result;
                        result = new PluginResult(PluginResult.Status.OK, status_code);
                        callbackctx.sendPluginResult(result);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        } catch (Exception e) {
            // TODO
        }
    }

    private void sendUSBConfig(final JSONArray args, final CallbackContext callbackctx) throws Exception  {

        cordova.getThreadPool().execute(new Runnable() {
            public void run() {

                Printer myPrinter = new Printer();

                Context context = cordova.getActivity().getApplicationContext();

                UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
                UsbDevice usbDevice = myPrinter.getUsbDevice(usbManager);
                if (usbDevice == null) {
                    Log.d(TAG, "USB device not found");
                    return;
                }

                final String ACTION_USB_PERMISSION = "com.threescreens.cordova.plugin.brotherPrinter.USB_PERMISSION";

                PendingIntent permissionIntent = PendingIntent.getBroadcast(context, 0, new Intent(ACTION_USB_PERMISSION), 0);
                usbManager.requestPermission(usbDevice, permissionIntent);

                final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        String action = intent.getAction();
                        if (ACTION_USB_PERMISSION.equals(action)) {
                            synchronized(this) {
                                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
                                    Log.d(TAG, "USB permission granted");
                                else
                                    Log.d(TAG, "USB permission rejected");
                            }
                        }
                    }
                };

                context.registerReceiver(mUsbReceiver, new IntentFilter(ACTION_USB_PERMISSION));

                while (true) {
                    if (!usbManager.hasPermission(usbDevice)) {
                        usbManager.requestPermission(usbDevice, permissionIntent);
                    } else {
                        break;
                    }

                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                PrinterInfo myPrinterInfo = new PrinterInfo();

                myPrinterInfo = myPrinter.getPrinterInfo();

                myPrinterInfo.printerModel = PrinterInfo.Model.QL_820NWB;
                myPrinterInfo.port = PrinterInfo.Port.USB;
                myPrinterInfo.paperSize = PrinterInfo.PaperSize.CUSTOM;

                myPrinter.setPrinterInfo(myPrinterInfo);

                LabelInfo myLabelInfo = new LabelInfo();

                myLabelInfo.labelNameIndex = myPrinter.checkLabelInPrinter();
                myLabelInfo.isAutoCut = true;
                myLabelInfo.isEndCut = true;
                myLabelInfo.isHalfCut = false;
                myLabelInfo.isSpecialTape = false;

                //label info must be set after setPrinterInfo, it's not in the docs
                myPrinter.setLabelInfo(myLabelInfo);


                try {
                    File outputDir = context.getCacheDir();
                    File outputFile = new File(outputDir.getPath() + "configure.prn");

                    FileWriter writer = new FileWriter(outputFile);
                    writer.write(args.optString(0, null));
                    writer.close();

                    PrinterStatus status = myPrinter.printFile(outputFile.toString());
                    outputFile.delete();

                    String status_code = "" + status.errorCode;

                    Log.d(TAG, "PrinterStatus: " + status_code);

                    PluginResult result;
                    result = new PluginResult(PluginResult.Status.OK, status_code);
                    callbackctx.sendPluginResult(result);

                } catch (IOException e) {
                    Log.d(TAG, "Temp file action failed: " + e.toString());
                }

            }
        });
    }

    public void sendSnmpRequest(final JSONArray args, final CallbackContext callbackctx) throws Exception {
        try {
            String resultString = "";



            resultString = getSnmpValue(args.optJSONArray(0).optString(0), args.optJSONArray(0).optString(1));
            final PluginResult result;

            result = new PluginResult(PluginResult.Status.OK, resultString);
            callbackctx.sendPluginResult(result);

        } catch (Exception e) {

            final PluginResult result;
            result = new PluginResult(PluginResult.Status.OK, "ERROR");
            callbackctx.sendPluginResult(result);

        }
    }

    public String getSnmpValue(String udpAddress, String oidString) throws Exception {

        String snmpResultText = "";

        Log.d(TAG, "SNMP GET Demo");

        // Create TransportMapping and Listen
        TransportMapping transport = new DefaultUdpTransportMapping();
        transport.listen();

        // Create Target Address object
        CommunityTarget comtarget = new CommunityTarget();
        comtarget.setCommunity(new OctetString("public"));
        comtarget.setVersion(SnmpConstants.version1);
        comtarget.setAddress(new UdpAddress(udpAddress));
        comtarget.setRetries(2);
        comtarget.setTimeout(1000);

        // Create the PDU object
        PDU pdu = new PDU();
        pdu.add(new VariableBinding(new OID(oidString)));
        pdu.setType(PDU.GET);
        pdu.setRequestID(new Integer32(1));

        // Create Snmp object for sending data to Agent
        Snmp snmp = new Snmp(transport);

        Log.d(TAG, "Sending Request to Agent...");
        ResponseEvent response = snmp.get(pdu, comtarget);

        // Process Agent Response
        if (response != null) {
            Log.d(TAG, "Got Response from Agent");
            PDU responsePDU = response.getResponse();

            if (responsePDU != null) {
                int errorStatus = responsePDU.getErrorStatus();
                int errorIndex = responsePDU.getErrorIndex();
                String errorStatusText = responsePDU.getErrorStatusText();

                if (errorStatus == PDU.noError) {
                    Log.d(TAG, "Snmp Get Response = " + responsePDU.getVariableBindings());

                    snmpResultText = responsePDU.getVariableBindings() + "";
                } else {
                    Log.d(TAG, "Error: Request Failed");
                    Log.d(TAG, "Error Status = " + errorStatus);
                    Log.d(TAG, "Error Index = " + errorIndex);
                    Log.d(TAG, "Error Status Text = " + errorStatusText);
                }
            } else {
                Log.d(TAG, "Error: Response PDU is null");
            }
        } else {
            Log.d(TAG, "Error: Agent Timeout... ");
        }
        snmp.close();

        return snmpResultText;
    }

}