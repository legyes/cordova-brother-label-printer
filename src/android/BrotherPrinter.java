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

public class BrotherPrinter extends CordovaPlugin {

	String modelName = "QL-810W";
	int copyCount = 1;
	boolean autoCut = true;
	boolean endCut = true;
	String labelType = "normal";
	Bitmap printBitmap;
	
	private NetPrinter[] netPrinters;

	private String ipAddress = null;
	private String macAddress = null;
	private Boolean searched = false;
	private Boolean found = false;

	//token to make it easy to grep logcat
	private static final String TAG = "print";

	private CallbackContext callbackctx;

	@Override
	public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {

		if ("findNetworkPrinters".equals(action)) {
			findNetworkPrinters(callbackContext);
			return true;
		}

		if ("printViaSDK".equals(action)) {
			printViaSDK(args, callbackContext);
			return true;
		}

		if ("sendUSBConfig".equals(action)) {
			sendUSBConfig(args, callbackContext);
			return true;
		}

		return false;
	}

	private NetPrinter[] enumerateNetPrinters() {
		Printer myPrinter = new Printer();
		PrinterInfo myPrinterInfo = new PrinterInfo();
		netPrinters = myPrinter.getNetPrinters(modelName);
		return netPrinters;
	}

	private void findNetworkPrinters(final CallbackContext callbackctx) {
		try {
			cordova.getThreadPool().execute(new Runnable() {
				public void run() {
					try {
	
						searched = true;
	
						NetPrinter[] netPrinters = enumerateNetPrinters();
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
		}
		catch(Exception e){
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
		paint.setTextAlign(Paint.Align.LEFT);
		float baseline = -paint.ascent();

		int width = 1320;
		int height = 495;
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

		return image;
	}
	
	public Bitmap labelTypeFrozenBitmap(String row1Title, String row2Title, String row2Text, String row3Title, String row3Text, String row4Title, String row4Text) {
		Paint paint = new Paint();
		paint.setTextSize(80);
		paint.setColor(Color.WHITE);
		paint.setTextAlign(Paint.Align.LEFT);
		float baseline = -paint.ascent();

		int width = 1320;
		int height = 495;
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
		int height = 525;
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
	
	private void printViaSDK(final JSONArray args, final CallbackContext callbackctx) {
		try {
		
			JSONObject j = new JSONObject();
			
			try {
				j = new JSONObject(args.optJSONArray(0).optString(0));
			} catch (JSONException e) {
				final PluginResult result;
				result = new PluginResult(PluginResult.Status.ERROR, "JSON error");
				callbackctx.sendPluginResult(result);
			}
			
			copyCount	= Integer.parseInt(j.optString("copy").toString());
			autoCut		= Boolean.parseBoolean(j.optString("autoCut").toString());
			endCut		= Boolean.parseBoolean(j.optString("endCut").toString());
			labelType	= j.optString("labelType").toString();
			
			if ( labelType.equalsIgnoreCase("meat") ) {
				printBitmap = labelTypeMeatBitmap(
					j.optString("text1"),
					j.optString("text2"),
					j.optString("text3"),
					j.optString("text4"),
					j.optString("text5"),
					j.optString("text6"),
					j.optString("text7")
				);
			}
			else if ( labelType.equalsIgnoreCase("frozen") ) {
				printBitmap = labelTypeFrozenBitmap(
					j.optString("text1"),
					j.optString("text2"),
					j.optString("text3"),
					j.optString("text4"),
					j.optString("text5"),
					j.optString("text6"),
					j.optString("text7")
				);
			}
			else if ( labelType.equalsIgnoreCase("custom") ){
				printBitmap = textAsBitmap(
					j.optString("text1"),
					j.optString("text2"),
					j.optString("text3"),
					j.optString("text4"),
					j.optString("text5")
				);
			}
			else {
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
	
						myPrinterInfo.printerModel = PrinterInfo.Model.QL_810W;
						myPrinterInfo.port = PrinterInfo.Port.NET;
						//myPrinterInfo.printMode     = PrinterInfo.PrintMode.ORIGINAL;
						myPrinterInfo.orientation = PrinterInfo.Orientation.PORTRAIT;
						myPrinterInfo.printMode = PrinterInfo.PrintMode.FIT_TO_PAGE;
						//myPrinterInfo.orientation   = PrinterInfo.Orientation.LANDSCAPE;
						//myPrinterInfo.printQuality	= PrinterInfo.PrintQuality.HIGH_RESOLUTION;
						myPrinterInfo.printQuality = PrinterInfo.PrintQuality.NORMAL;
						myPrinterInfo.numberOfCopies = copyCount;
	
						myPrinterInfo.paperSize = PrinterInfo.PaperSize.CUSTOM;
						myPrinterInfo.ipAddress = ipAddress;
						myPrinterInfo.macAddress = macAddress;
						myPrinter.setPrinterInfo(myPrinterInfo);
	
						LabelInfo myLabelInfo = new LabelInfo();
	
						//myLabelInfo.labelNameIndex  = myPrinter.checkLabelInPrinter();
						//myLabelInfo.labelNameIndex  = 9; // W62H29
						myLabelInfo.labelNameIndex = LabelInfo.QL700.valueOf("W62H29").ordinal();
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
		}
		catch(Exception e){
			// TODO
		}
	}

	private void sendUSBConfig(final JSONArray args, final CallbackContext callbackctx) {

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

				myPrinterInfo.printerModel = PrinterInfo.Model.QL_810W;
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

}