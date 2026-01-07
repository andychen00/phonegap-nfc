package com.chariotsolutions.nfc.plugin;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.net.Uri;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.nfc.Tag;
import android.nfc.TagLostException;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.os.Parcelable;
import android.util.Log;
import android.nfc.tech.IsoDep;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Arrays;
import java.io.IOException;
import java.util.Date;


// using wildcard imports so we can support Cordova 3.x

public class NfcPlugin extends CordovaPlugin implements NfcAdapter.OnNdefPushCompleteCallback {
    private static final String REGISTER_MIME_TYPE = "registerMimeType";
    private static final String REMOVE_MIME_TYPE = "removeMimeType";
    private static final String REGISTER_NDEF = "registerNdef";
    private static final String REMOVE_NDEF = "removeNdef";
    private static final String REGISTER_NDEF_FORMATABLE = "registerNdefFormatable";
    private static final String REGISTER_DEFAULT_TAG = "registerTag";
    private static final String REMOVE_DEFAULT_TAG = "removeTag";
    private static final String WRITE_TAG = "writeTag";
    private static final String MAKE_READ_ONLY = "makeReadOnly";
    private static final String ERASE_TAG = "eraseTag";
    private static final String SHARE_TAG = "shareTag";
    private static final String UNSHARE_TAG = "unshareTag";
    private static final String HANDOVER = "handover"; // Android Beam
    private static final String STOP_HANDOVER = "stopHandover";
    private static final String ENABLED = "enabled";
    private static final String INIT = "init";
    private static final String SHOW_SETTINGS = "showSettings";

    private static final String NDEF = "ndef";
    private static final String NDEF_MIME = "ndef-mime";
    private static final String NDEF_FORMATABLE = "ndef-formatable";
    private static final String TAG_DEFAULT = "tag";

    private static final String STATUS_NFC_OK = "NFC_OK";
    private static final String STATUS_NO_NFC = "NO_NFC";
    private static final String STATUS_NFC_DISABLED = "NFC_DISABLED";
    private static final String STATUS_NDEF_PUSH_DISABLED = "NDEF_PUSH_DISABLED";

    private static final String TAG = "NfcPlugin";
    private final List<IntentFilter> intentFilters = new ArrayList<IntentFilter>();
    private final ArrayList<String[]> techLists = new ArrayList<String[]>();

    private NdefMessage p2pMessage = null;
    private PendingIntent pendingIntent = null;

    private Intent savedIntent = null;

    private CallbackContext shareTagCallback;
    private CallbackContext handoverCallback;

    private CallbackContext ndefCallback;

    @Override
    public boolean execute(String action, JSONArray data, CallbackContext callbackContext) throws JSONException {

        Log.d(TAG, "execute " + action);

        // showSettings can be called if NFC is disabled
        // might want to skip this if NO_NFC
        if (action.equalsIgnoreCase(SHOW_SETTINGS)) {
            showSettings(callbackContext);
            return true;
        }

        if (!getNfcStatus().equals(STATUS_NFC_OK)) {
            callbackContext.error(getNfcStatus());
            return true; // short circuit
        }

        createPendingIntent();

        if (action.equalsIgnoreCase(REGISTER_MIME_TYPE)) {
            registerMimeType(data, callbackContext);

        } else if (action.equalsIgnoreCase(REMOVE_MIME_TYPE)) {
          removeMimeType(data, callbackContext);

        } else if (action.equalsIgnoreCase(REGISTER_NDEF)) {
          registerNdef(callbackContext);

        } else if (action.equalsIgnoreCase(REMOVE_NDEF)) {
          removeNdef(callbackContext);

        } else if (action.equalsIgnoreCase(REGISTER_NDEF_FORMATABLE)) {
            registerNdefFormatable(callbackContext);

        } else if (action.equalsIgnoreCase("addNdefListener")) {
            Log.d(TAG, "📱 JavaScript registering NFC listener");
    
            this.ndefCallback = callbackContext;
            PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
            result.setKeepCallback(true);
            callbackContext.sendPluginResult(result);
    
            // 🚨 TAMBAH INI: Auto-setup jika kosong
            if (intentFilters.isEmpty()) {
                Log.d(TAG, "➕ Adding default intent filters");
                try {
                    intentFilters.add(new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED));
                    intentFilters.add(new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED));
                    intentFilters.add(new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED));
                } catch (Exception e) {
                    Log.e(TAG, "Error adding filters", e);
                }
            }
            return true;

        }  else if (action.equals(REGISTER_DEFAULT_TAG)) {
          registerDefaultTag(callbackContext);

        }  else if (action.equals(REMOVE_DEFAULT_TAG)) {
          removeDefaultTag(callbackContext);

        } else if (action.equalsIgnoreCase(WRITE_TAG)) {
            writeTag(data, callbackContext);

        } else if (action.equalsIgnoreCase(MAKE_READ_ONLY)) {
            makeReadOnly(callbackContext);

        } else if (action.equalsIgnoreCase(ERASE_TAG)) {
            eraseTag(callbackContext);

        } else if (action.equalsIgnoreCase(SHARE_TAG)) {
            shareTag(data, callbackContext);

        } else if (action.equalsIgnoreCase(UNSHARE_TAG)) {
            unshareTag(callbackContext);

        } else if (action.equalsIgnoreCase(HANDOVER)) {
            handover(data, callbackContext);

        } else if (action.equalsIgnoreCase(STOP_HANDOVER)) {
            stopHandover(callbackContext);

        } else if (action.equalsIgnoreCase(INIT)) {
            init(callbackContext);

        } else if (action.equalsIgnoreCase(ENABLED)) {
            // status is checked before every call
            // if code made it here, NFC is enabled
            callbackContext.success(STATUS_NFC_OK);

        } else {
            // invalid action
            return false;
        }

        return true;
    }

    private String getNfcStatus() {
        NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(getActivity());
        if (nfcAdapter == null) {
            return STATUS_NO_NFC;
        } else if (!nfcAdapter.isEnabled()) {
            return STATUS_NFC_DISABLED;
        } else {
            return STATUS_NFC_OK;
        }
    }

    private void registerDefaultTag(CallbackContext callbackContext) {
      addTagFilter();
      callbackContext.success();
  }

    private void removeDefaultTag(CallbackContext callbackContext) {
      removeTagFilter();
      callbackContext.success();
  }

    private void registerNdefFormatable(CallbackContext callbackContext) {
        addTechList(new String[]{NdefFormatable.class.getName()});
        callbackContext.success();
    }

    private void registerNdef(CallbackContext callbackContext) {
      addTechList(new String[]{Ndef.class.getName()});
      callbackContext.success();
  }

    private void removeNdef(CallbackContext callbackContext) {
      removeTechList(new String[]{Ndef.class.getName()});
      callbackContext.success();
  }

    private void unshareTag(CallbackContext callbackContext) {
        p2pMessage = null;
        stopNdefPush();
        shareTagCallback = null;
        callbackContext.success();
    }

    private void init(CallbackContext callbackContext) {
        Log.d(TAG, "Enabling plugin " + getIntent());

        startNfc();
        if (!recycledIntent()) {
            parseMessage();
        }
        callbackContext.success();
    }

    private void removeMimeType(JSONArray data, CallbackContext callbackContext) throws JSONException {
        String mimeType = "";
        try {
            mimeType = data.getString(0);
            /*boolean removed =*/ removeIntentFilter(mimeType);
            callbackContext.success();
        } catch (MalformedMimeTypeException e) {
            callbackContext.error("Invalid MIME Type " + mimeType);
        }
    }

    private void registerMimeType(JSONArray data, CallbackContext callbackContext) throws JSONException {
        String mimeType = "";
        try {
            mimeType = data.getString(0);
            intentFilters.add(createIntentFilter(mimeType));
            callbackContext.success();
        } catch (MalformedMimeTypeException e) {
            callbackContext.error("Invalid MIME Type " + mimeType);
        }
    }

    // Cheating and writing an empty record. We may actually be able to erase some tag types.
    private void eraseTag(CallbackContext callbackContext) throws JSONException {
        Tag tag = savedIntent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        NdefRecord[] records = {
            new NdefRecord(NdefRecord.TNF_EMPTY, new byte[0], new byte[0], new byte[0])
        };
        writeNdefMessage(new NdefMessage(records), tag, callbackContext);
    }

    private void writeTag(JSONArray data, CallbackContext callbackContext) throws JSONException {
        if (getIntent() == null) {  // TODO remove this and handle LostTag
            callbackContext.error("Failed to write tag, received null intent");
            return;
        }

        Tag tag = savedIntent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        NdefRecord[] records = Util.jsonToNdefRecords(data.getString(0));
        writeNdefMessage(new NdefMessage(records), tag, callbackContext);
    }

    private void writeNdefMessage(final NdefMessage message, final Tag tag, final CallbackContext callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Ndef ndef = Ndef.get(tag);
                    if (ndef != null && !ndef.isConnected()) {
                        ndef.connect();

                        if (ndef.isWritable()) {
                            int size = message.toByteArray().length;
                            if (ndef.getMaxSize() < size) {
                                callbackContext.error("Tag capacity is " + ndef.getMaxSize() +
                                        " bytes, message is " + size + " bytes.");
                            } else {
                                ndef.writeNdefMessage(message);
                                callbackContext.success();
                            }
                        } else {
                            callbackContext.error("Tag is read only");
                        }
                        ndef.close();
                    } else {
                        NdefFormatable formatable = NdefFormatable.get(tag);
                        if (formatable != null) {
                            formatable.connect();
                            formatable.format(message);
                            callbackContext.success();
                            formatable.close();
                        } else {
                            callbackContext.error("Tag doesn't support NDEF");
                        }
                    }
                } catch (FormatException e) {
                    callbackContext.error(e.getMessage());
                } catch (TagLostException e) {
                    callbackContext.error(e.getMessage());
                } catch (IOException e) {
                    callbackContext.error(e.getMessage());
                } catch (IllegalStateException e) {
                	callbackContext.error("Still writing!");
                    Log.e(TAG, "Illegal State, still writing !");
                }
            }
        });
    }

    private void makeReadOnly(final CallbackContext callbackContext) throws JSONException {

        if (getIntent() == null) { // Lost Tag
            callbackContext.error("Failed to make tag read only, received null intent");
            return;
        }

        final Tag tag = savedIntent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        if (tag == null) {
            callbackContext.error("Failed to make tag read only, tag is null");
            return;
        }

        cordova.getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                boolean success = false;
                String message = "Could not make tag read only";

                Ndef ndef = Ndef.get(tag);

                try {
                    if (ndef != null) {

                        ndef.connect();

                        if (!ndef.isWritable()) {
                            message = "Tag is not writable";
                        } else if (ndef.canMakeReadOnly()) {
                            success = ndef.makeReadOnly();
                        } else {
                            message = "Tag can not be made read only";
                        }

                    } else {
                        message = "Tag is not NDEF";
                    }

                } catch (IOException e) {
                    Log.e(TAG, "Failed to make tag read only", e);
                    if (e.getMessage() != null) {
                        message = e.getMessage();
                    } else {
                        message = e.toString();
                    }
                }

                if (success) {
                    callbackContext.success();
                } else {
                    callbackContext.error(message);
                }
            }
        });
    }

    private void shareTag(JSONArray data, CallbackContext callbackContext) throws JSONException {
        NdefRecord[] records = Util.jsonToNdefRecords(data.getString(0));
        this.p2pMessage = new NdefMessage(records);

        startNdefPush(callbackContext);
    }

    // setBeamPushUris
    // Every Uri you provide must have either scheme 'file' or scheme 'content'.
    // Note that this takes priority over setNdefPush
    //
    // See http://developer.android.com/reference/android/nfc/NfcAdapter.html#setBeamPushUris(android.net.Uri[],%20android.app.Activity)
    private void handover(JSONArray data, CallbackContext callbackContext) throws JSONException {

        Uri[] uri = new Uri[data.length()];

        for (int i = 0; i < data.length(); i++) {
            uri[i] = Uri.parse(data.getString(i));
        }

        startNdefBeam(callbackContext, uri);
    }

    private void stopHandover(CallbackContext callbackContext) throws JSONException {
        stopNdefBeam();
        handoverCallback = null;
        callbackContext.success();
    }

    private void showSettings(CallbackContext callbackContext) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
            Intent intent = new Intent(android.provider.Settings.ACTION_NFC_SETTINGS);
            getActivity().startActivity(intent);
        } else {
            Intent intent = new Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS);
            getActivity().startActivity(intent);
        }
        callbackContext.success();
    }

    private void createPendingIntent() {
        if (pendingIntent == null) {
            Activity activity = getActivity();
            Intent intent = new Intent(activity, activity.getClass());
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                pendingIntent = PendingIntent.getActivity(activity, 0, intent, PendingIntent.FLAG_MUTABLE);
            } else {
                pendingIntent = PendingIntent.getActivity(activity, 0, intent, 0);
            }
        }
    }

    private void addTechList(String[] list) {
      this.addTechFilter();
      this.addToTechList(list);
    }

    private void removeTechList(String[] list) {
      this.removeTechFilter();
      this.removeFromTechList(list);
    }

    private void addTechFilter() {
      intentFilters.add(new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED));
    }

    private boolean removeTechFilter() {
      boolean removed = false;
      Iterator<IntentFilter> iter = intentFilters.iterator();
      while (iter.hasNext()) {
        IntentFilter intentFilter = iter.next();
        if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(intentFilter.getAction(0))) {
          iter.remove();
          removed = true;
        }
      }
      return removed;
    }

    private void addTagFilter() {
      intentFilters.add(new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED));
  }

    private boolean removeTagFilter() {
      boolean removed = false;
      Iterator<IntentFilter> iter = intentFilters.iterator();
      while (iter.hasNext()) {
        IntentFilter intentFilter = iter.next();
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(intentFilter.getAction(0))) {
          iter.remove();
          removed = true;
        }
      }
      return removed;
  }

   private void startNfc() {
    Log.d(TAG, "🚀 startNfc() called");
    
    createPendingIntent();
    
    getActivity().runOnUiThread(new Runnable() {
        public void run() {
            try {
                NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(getActivity());
                
                if (nfcAdapter == null) {
                    Log.e(TAG, "❌ Device tidak support NFC");
                    return;
                }
                
                if (!nfcAdapter.isEnabled()) {
                    Log.e(TAG, "❌ NFC tidak aktif di device");
                    // Bisa trigger ke JavaScript untuk show alert
                    return;
                }
                
                Log.d(TAG, "✅ NFC Adapter ready, enabling foreground dispatch");
                Log.d(TAG, "📋 Intent Filters: " + intentFilters.size());
                Log.d(TAG, "📋 Tech Lists: " + techLists.size());
                
                IntentFilter[] filters = getIntentFilters();
                String[][] techListsArray = getTechLists();
                
                nfcAdapter.enableForegroundDispatch(
                    getActivity(), 
                    getPendingIntent(), 
                    filters, 
                    techListsArray
                );
                
                Log.d(TAG, "🎉 NFC FOREGROUND DISPATCH ENABLED SUCCESSFULLY");
                
            } catch (SecurityException e) {
                Log.e(TAG, "❌ SecurityException - Missing NFC permission?", e);
            } catch (IllegalStateException e) {
                Log.e(TAG, "❌ IllegalStateException - Activity not in foreground?", e);
            } catch (Exception e) {
                Log.e(TAG, "❌ Error enabling NFC", e);
            }
        }
    });
}

    private void stopNfc() {
        Log.d(TAG, "stopNfc");
        getActivity().runOnUiThread(new Runnable() {
            public void run() {

                NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(getActivity());

                if (nfcAdapter != null) {
                    try {
                        nfcAdapter.disableForegroundDispatch(getActivity());
                    } catch (IllegalStateException e) {
                        // issue 125 - user exits app with back button while nfc
                        Log.w(TAG, "Illegal State Exception stopping NFC. Assuming application is terminating.");
                    }
                }
            }
        });
    }

    private void startNdefBeam(final CallbackContext callbackContext, final Uri[] uris) {
        getActivity().runOnUiThread(new Runnable() {
            public void run() {

                NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(getActivity());

                if (nfcAdapter == null) {
                    callbackContext.error(STATUS_NO_NFC);
                } //else if (!nfcAdapter.isNdefPushEnabled()) {
                    //callbackContext.error(STATUS_NDEF_PUSH_DISABLED);
                //} 
                else {
                    //nfcAdapter.setOnNdefPushCompleteCallback(NfcPlugin.this, getActivity());
                    try {
                        //nfcAdapter.setBeamPushUris(uris, getActivity());

                        PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
                        result.setKeepCallback(true);
                        handoverCallback = callbackContext;
                        callbackContext.sendPluginResult(result);

                    } catch (IllegalArgumentException e) {
                        callbackContext.error(e.getMessage());
                    }
                }
            }
        });
    }

    private void startNdefPush(final CallbackContext callbackContext) {
        getActivity().runOnUiThread(new Runnable() {
            public void run() {

                NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(getActivity());

                if (nfcAdapter == null) {
                    callbackContext.error(STATUS_NO_NFC);
                } //else if (!nfcAdapter.isNdefPushEnabled()) {
                    //callbackContext.error(STATUS_NDEF_PUSH_DISABLED);
                //} 
                else {
                    //nfcAdapter.setNdefPushMessage(p2pMessage, getActivity());
                    //nfcAdapter.setOnNdefPushCompleteCallback(NfcPlugin.this, getActivity());

                    PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
                    result.setKeepCallback(true);
                    shareTagCallback = callbackContext;
                    callbackContext.sendPluginResult(result);
                }
            }
        });
    }

    private void stopNdefPush() {
        getActivity().runOnUiThread(new Runnable() {
            public void run() {

                NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(getActivity());

                if (nfcAdapter != null) {
                    //nfcAdapter.setNdefPushMessage(null, getActivity());
                }

            }
        });
    }

    private void stopNdefBeam() {
        getActivity().runOnUiThread(new Runnable() {
            public void run() {

                NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(getActivity());

                if (nfcAdapter != null) {
                    //nfcAdapter.setBeamPushUris(null, getActivity());
                }

            }
        });
    }

    private void addToTechList(String[] techs) {
      techLists.add(techs);
  }

    private void removeFromTechList(String[] techs) {
      techLists.remove(techs);
  }

    private boolean removeIntentFilter(String mimeType) throws MalformedMimeTypeException {
      boolean removed = false;
      Iterator<IntentFilter> iter = intentFilters.iterator();
      while (iter.hasNext()) {
        IntentFilter intentFilter = iter.next();
        if(intentFilter.countDataTypes() > 0 ) {
            String mt = intentFilter.getDataType(0);
            if (mimeType.equals(mt)) {
                iter.remove();
                removed = true;
            }
        }
      }
      return removed;
    }

    private IntentFilter createIntentFilter(String mimeType) throws MalformedMimeTypeException {
        IntentFilter intentFilter = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
        intentFilter.addDataType(mimeType);
        return intentFilter;
    }

    private PendingIntent getPendingIntent() {
        return pendingIntent;
    }

    private IntentFilter[] getIntentFilters() {
        return intentFilters.toArray(new IntentFilter[intentFilters.size()]);
    }

    private String[][] getTechLists() {
        //noinspection ToArrayCallWithZeroLengthArrayArgument
        return techLists.toArray(new String[0][0]);
    }

    // change-start --------------------------------------------------
    
  private void processLivinCard(Tag tag) {
    Log.d(TAG, "💰 PROCESS LIVIN MANDIRI CARD");
    
    cordova.getThreadPool().execute(new Runnable() {
        @Override
        public void run() {
            JSONObject result = new JSONObject();
            
            try {
                // 1. GET ISO DEP
                IsoDep isoDep = IsoDep.get(tag);
                if (isoDep == null) {
                    safePut(result, "success", false);
                    safePut(result, "message", "Bukan kartu bank");
                    sendToJS(result);
                    return;
                }
                
                // 2. CONNECT
                isoDep.connect();
                isoDep.setTimeout(10000);
                
                // 🚨 3. ALL APDU COMMANDS
                
                // 3.1 SELECT eMoney
                Log.d(TAG, "SELECT command...");
                String selectHex = "00A40400080000000000000001";
                byte[] selectCmd = hexStringToByteArray(selectHex);
                byte[] selectResp = isoDep.transceive(selectCmd);
                
                if (!isSuccessAPDU(selectResp)) {
                    safePut(result, "success", false);
                    safePut(result, "message", "Bukan kartu Livin Mandiri");
                    isoDep.close();
                    sendToJS(result);
                    return;
                }
                
                // 3.2 ATTRIBUTE (11 byte)
                Log.d(TAG, "ATTRIBUTE command...");
                String attrHex = "00F210000B";
                byte[] attrCmd = hexStringToByteArray(attrHex);
                byte[] attrResp = isoDep.transceive(attrCmd);
                
                // 3.3 UID
                Log.d(TAG, "UID command...");
                String uidHex = "FFCA000000";
                byte[] uidCmd = hexStringToByteArray(uidHex);
                byte[] uidResp = isoDep.transceive(uidCmd);
                
                // 3.4 INFO (63 byte)
                Log.d(TAG, "INFO command...");
                String infoHex = "00B300003F";
                byte[] infoCmd = hexStringToByteArray(infoHex);
                byte[] infoResp = isoDep.transceive(infoCmd);
                
                // 3.5 BALANCE
                Log.d(TAG, "BALANCE command...");
                String balanceHex = "00B500000A";
                byte[] balanceCmd = hexStringToByteArray(balanceHex);
                byte[] balanceResp = isoDep.transceive(balanceCmd);
                
                // 4. PARSE RESULTS
                safePut(result, "success", true);
                safePut(result, "message", "Kartu Livin Mandiri terbaca");
                safePut(result, "cardType", "LIVIN_MANDIRI");
                
                // UID
                if (isSuccessAPDU(uidResp) && uidResp.length >= 6) {
                    int uidLen = uidResp.length - 2;
                    byte[] uidData = Arrays.copyOfRange(uidResp, 0, uidLen);
                    safePut(result, "uid", bytesToHex(uidData));
                }
                
                // BALANCE
                if (isSuccessAPDU(balanceResp) && balanceResp.length >= 6) {
                    int balanceLen = balanceResp.length - 2;
                    
                    if (balanceLen == 4) {
                        int balance = ((balanceResp[0] & 0xFF) << 0) |
                                     ((balanceResp[1] & 0xFF) << 8) |
                                     ((balanceResp[2] & 0xFF) << 16) |
                                     ((balanceResp[3] & 0xFF) << 24);
                        safePut(result, "balance", balance);
                        safePut(result, "appletType", "NEW");
                        
                    } else if (balanceLen == 10) {
                        safePut(result, "appletType", "OLD");
                        safePut(result, "balanceRaw", bytesToHex(Arrays.copyOfRange(balanceResp, 0, 10)));
                    }
                }
                
                // ATTRIBUTE
                if (isSuccessAPDU(attrResp) && attrResp.length >= 13) {
                    byte[] attrData = Arrays.copyOfRange(attrResp, 0, 11);
                    safePut(result, "attribute", bytesToHex(attrData));
                }
                
                // INFO
                if (isSuccessAPDU(infoResp) && infoResp.length >= 65) {
                    byte[] infoData = Arrays.copyOfRange(infoResp, 0, 63);
                    safePut(result, "info", bytesToHex(infoData));
                }
                
                isoDep.close();
                Log.d(TAG, "✅ All APDU commands completed");
                
            } catch (IOException e) {
                safePut(result, "success", false);
                safePut(result, "message", "Kartu terlepas: " + e.getMessage());
                Log.e(TAG, "IO Error", e);
            } catch (Exception e) {
                safePut(result, "success", false);
                safePut(result, "message", "Error: " + e.getMessage());
                Log.e(TAG, "Error", e);
            }
            
            // 5. SEND TO JAVASCRIPT
            sendToJS(result);
        }
    });
}

// HELPER METHOD UNTUK AMAN PUT JSON
private void safePut(JSONObject obj, String key, Object value) {
    try {
        obj.put(key, value);
    } catch (JSONException e) {
        Log.e(TAG, "JSON Error: " + key + "=" + value, e);
    }
}

    void parseMessage() {
    cordova.getThreadPool().execute(new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "parseMessage " + getIntent());
            Intent intent = getIntent();
            String action = intent.getAction();
            Log.d(TAG, "action " + action);
            
            if (action == null) {
                return;
            }

            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            Parcelable[] messages = intent.getParcelableArrayExtra((NfcAdapter.EXTRA_NDEF_MESSAGES));

            // 🚨 CEK APAKAH KARTU BANK
            if (tag != null) {
                boolean isBankCard = false;
                String[] techList = tag.getTechList();
                
                for (String tech : techList) {
                    Log.d(TAG, "Tech: " + tech);
                    if (tech.contains("IsoDep")) {
                        isBankCard = true;
                        break;
                    }
                }
                
                if (isBankCard) {
                    Log.d(TAG, "🔄 Processing as Livin card...");
                    processLivinCard(tag);
                    return; // STOP NDEF PROCESSING
                }
            }
            
            // 🚨 PROCESS NDEF NORMAL (untuk NFC biasa)
            if (action.equals(NfcAdapter.ACTION_NDEF_DISCOVERED)) {
                Ndef ndef = Ndef.get(tag);
                boolean sendNdefMimeEvent = false;
                if(messages.length == 1){
                    NdefMessage message = (NdefMessage) messages[0];
                    for(NdefRecord record : message.getRecords()) {
                        sendNdefMimeEvent = record.getTnf() == NdefRecord.TNF_MIME_MEDIA;
                        break;
                    }
                }
                if(sendNdefMimeEvent) {
                    fireNdefEvent(NDEF_MIME, ndef, messages);
                }
                
                fireNdefEvent(NDEF, ndef, messages);

            } else if (action.equals(NfcAdapter.ACTION_TECH_DISCOVERED)) {
                for (String tagTech : tag.getTechList()) {
                    Log.d(TAG, tagTech);
                    if (tagTech.equals(NdefFormatable.class.getName())) {
                        fireNdefFormatableEvent(tag);
                    } else if (tagTech.equals(Ndef.class.getName())) {
                        Ndef ndef = Ndef.get(tag);
                        fireNdefEvent(NDEF, ndef, messages);
                    }
                }
            }

            if (action.equals(NfcAdapter.ACTION_TAG_DISCOVERED)) {
                fireTagEvent(tag);
            }

            setIntent(new Intent());
        }
    });
}
// HEX TO BYTE ARRAY
private byte[] hexStringToByteArray(String s) {
    if (s == null || s.length() % 2 != 0) {
        Log.e(TAG, "Invalid hex string: " + s);
        return new byte[0];
    }
    byte[] data = new byte[s.length() / 2];
    for (int i = 0; i < s.length(); i += 2) {
        data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                             + Character.digit(s.charAt(i+1), 16));
    }
    return data;
}

// BYTES TO HEX STRING
private String bytesToHex(byte[] bytes) {
    if (bytes == null) return "null";
    StringBuilder sb = new StringBuilder();
    for (byte b : bytes) {
        sb.append(String.format("%02X", b));
    }
    return sb.toString();
}

// CHECK APDU SUCCESS (90 00)
private boolean isSuccessAPDU(byte[] response) {
    if (response == null || response.length < 2) {
        Log.d(TAG, "Invalid APDU response");
        return false;
    }
    byte sw1 = response[response.length - 2];
    byte sw2 = response[response.length - 1];
    boolean success = (sw1 & 0xFF) == 0x90 && (sw2 & 0xFF) == 0x00;
    Log.d(TAG, "APDU Status: " + String.format("%02X%02X", sw1 & 0xFF, sw2 & 0xFF));
    return success;
}

// SEND TO JAVASCRIPT
private void sendToJS(JSONObject data) {
    if (ndefCallback == null) {
        Log.e(TAG, "❌ No JavaScript callback");
        return;
    }
    try {
        PluginResult result = new PluginResult(PluginResult.Status.OK, data);
        result.setKeepCallback(true);
        ndefCallback.sendPluginResult(result);
        Log.d(TAG, "✅ Sent to JS");
    } catch (Exception e) {
        Log.e(TAG, "❌ Error sending to JS", e);
    }
}
    // change-end--------------------------------------------------

    private void fireNdefEvent(String type, Ndef ndef, Parcelable[] messages) {
        if (ndefCallback == null) return;

        JSONObject jsonObject = buildNdefJSON(ndef, messages);
        String tag = jsonObject.toString();

        Log.v(TAG, tag);
        PluginResult result = new PluginResult(PluginResult.Status.OK, jsonObject);
        result.setKeepCallback(true); // listener tetap hidup
        ndefCallback.sendPluginResult(result);
    }

    private void fireNdefFormatableEvent (Tag tag) {
        if (ndefCallback == null) return; 
        Log.v(TAG, tag.toString());
        JSONObject tagJson = Util.tagToJSON(tag);
        PluginResult result = new PluginResult(PluginResult.Status.OK, tagJson);
        result.setKeepCallback(true); // listener tetap hidup
        ndefCallback.sendPluginResult(result);
    }

    private void fireTagEvent(Tag tag) {
        Log.d(TAG, "🔥 Firing tag event");
    
        if (ndefCallback == null) {
            Log.e(TAG, "❌ No callback registered");
            return;
        }
        
        try {
            JSONObject result = Util.tagToJSON(tag);
        
            // 🚨 TAMBAH DETEKSI KARTU BANK
            String[] techList = tag.getTechList();
            for (String tech : techList) {
                if (tech.contains("IsoDep")) {
                    result.put("cardType", "BANK_CARD_DETECTED");
                    result.put("message", "This appears to be a bank card. Try Livin processing.");
                    break;
                }
            }
        
            Log.d(TAG, "📤 Sending tag data");
        
            PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, result);
            pluginResult.setKeepCallback(true);
            ndefCallback.sendPluginResult(pluginResult);
        
        } catch (Exception e) {
            Log.e(TAG, "Error in fireTagEvent", e);
        }
    }

    JSONObject buildNdefJSON(Ndef ndef, Parcelable[] messages) {

        JSONObject json = Util.ndefToJSON(ndef);

        // ndef is null for peer-to-peer
        // ndef and messages are null for ndef format-able
        if (ndef == null && messages != null) {

            try {

                if (messages.length > 0) {
                    NdefMessage message = (NdefMessage) messages[0];
                    json.put("ndefMessage", Util.messageToJSON(message));
                    // guessing type, would prefer a more definitive way to determine type
                    json.put("type", "NDEF Push Protocol");
                }

                if (messages.length > 1) {
                    Log.wtf(TAG, "Expected one ndefMessage but found " + messages.length);
                }

            } catch (JSONException e) {
                // shouldn't happen
                Log.e(Util.TAG, "Failed to convert ndefMessage into json", e);
            }
        }
        return json;
    }

    private boolean recycledIntent() { // TODO this is a kludge, find real solution

        int flags = getIntent().getFlags();
        if ((flags & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) {
            Log.i(TAG, "Launched from history, killing recycled intent");
            setIntent(new Intent());
            return true;
        }
        return false;
    }

    @Override
    public void onPause(boolean multitasking) {
        Log.d(TAG, "onPause " + getIntent());
        super.onPause(multitasking);
        if (multitasking) {
            // nfc can't run in background
            stopNfc();
        }
    }

    @Override
    public void onResume(boolean multitasking) {
        Log.d(TAG, "onResume " + getIntent());
        super.onResume(multitasking);
        startNfc();
    }

    @Override
    public void onReset() {
        super.onReset();
        intentFilters.clear();
        techLists.clear();
    }

    @Override
    public void onNewIntent(Intent intent) {
    Log.d(TAG, "🎯 NFC INTENT RECEIVED: " + intent.getAction());
    super.onNewIntent(intent);
    setIntent(intent);
    savedIntent = intent;
    
    // 🚨🚨🚨 INI YANG PENTING 🚨🚨🚨
    parseMessage(); // UNCOMMENT LINE INI ATAU TAMBAHKAN
    }

    private Activity getActivity() {
        return this.cordova.getActivity();
    }

    private Intent getIntent() {
        return getActivity().getIntent();
    }

    private void setIntent(Intent intent) {
        getActivity().setIntent(intent);
    }

    String javaScriptEventTemplate =
        "var e = document.createEvent(''Events'');\n" +
        "e.initEvent(''{0}'');\n" +
        "e.tag = {1};\n" +
        "document.dispatchEvent(e);";

    @Override
    public void onNdefPushComplete(NfcEvent event) {

        // handover (beam) take precedence over share tag (ndef push)
        if (handoverCallback != null) {
            PluginResult result = new PluginResult(PluginResult.Status.OK, "Beamed Message to Peer");
            result.setKeepCallback(true);
            handoverCallback.sendPluginResult(result);
        } else if (shareTagCallback != null) {
            PluginResult result = new PluginResult(PluginResult.Status.OK, "Shared Message with Peer");
            result.setKeepCallback(true);
            shareTagCallback.sendPluginResult(result);
        }

    }
}
