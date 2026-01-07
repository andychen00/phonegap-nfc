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
            
            // 🚨 TAMBAHAN UNTUK CEK KARTU BANK
            if (tag != null) {
                String[] techList = tag.getTechList();
                for (String tech : techList) {
                    if (tech.equals("android.nfc.tech.IsoDep")) {
                        Log.d(TAG, "🎯 KARTU BANK DETECTED (IsoDep)");
                        processLivinCard(tag);
                        return; // Stop processing biasa
                    }
                }
            }
            
            // ... sisa kode NDEF processing tetap ...

            setIntent(new Intent());
            }    
        });
    }

    // 🚨 TAMBAH METHOD BARU UNTUK LIVIN CARD
    private void processLivinCard(Tag tag) {
    Log.d(TAG, "💰 PROCESSING LIVIN MANDIRI CARD - UPDATED");
    
    cordova.getThreadPool().execute(new Runnable() {
        @Override
        public void run() {
            try {
                IsoDep isoDep = IsoDep.get(tag);
                if (isoDep == null) {
                    Log.e(TAG, "❌ Not an IsoDep card");
                    return;
                }
                
                isoDep.connect();
                isoDep.setTimeout(15000);
                Log.d(TAG, "✅ Connected to Livin card");
                
                JSONObject cardData = new JSONObject();
                cardData.put("cardType", "LIVIN_MANDIRI_EMONEY");
                cardData.put("timestamp", new Date().toString());
                
                // 🚨 APDU COMMAND FLOW - SESUAI SPESIFIKASI BARU
                
                // 1. SELECT eMoney Applet
                Log.d(TAG, "Step 1: SELECT eMoney Applet");
                String selectCmdHex = "00A40400080000000000000001"; // GANTI DENGAN HEX ASLI
                byte[] selectResponse = sendAPDUCommand(isoDep, selectCmdHex);
                
                if (isSuccessAPDU(selectResponse)) {
                    cardData.put("step1_select", "SUCCESS");
                    Log.d(TAG, "✅ Applet selected (9000)");
                    
                    // 2. GET CARD ATTRIBUTE (For Old Applet Only)
                    Log.d(TAG, "Step 2: GET CARD ATTRIBUTE");
                    String attrCmdHex = "00F210000B"; // GANTI DENGAN HEX ASLI
                    byte[] attrResponse = sendAPDUCommand(isoDep, attrCmdHex);
                    
                    if (isSuccessAPDU(attrResponse)) {
                        // Response: data 11 byte + 9000
                        int totalLength = attrResponse.length;
                        int dataLength = totalLength - 2; // Kurangi 2 byte status
                        
                        if (dataLength >= 11) {
                            byte[] attributeData = Arrays.copyOfRange(attrResponse, 0, 11);
                            cardData.put("cardAttribute", bytesToHex(attributeData));
                            cardData.put("attrLength", dataLength);
                            Log.d(TAG, "✅ Attribute: " + bytesToHex(attributeData) + 
                                  " (" + dataLength + " bytes)");
                        } else {
                            Log.w(TAG, "⚠️ Attribute data shorter than expected: " + dataLength);
                        }
                    }
                    
                    // 3. GET CARD UID
                    Log.d(TAG, "Step 3: GET CARD UID");
                    String uidCmdHex = "FFCA000000"; // GANTI DENGAN HEX ASLI
                    byte[] uidResponse = sendAPDUCommand(isoDep, uidCmdHex);
                    
                    if (isSuccessAPDU(uidResponse)) {
                        // Response: data 4 byte / 7 byte + 9000
                        int uidDataLength = uidResponse.length - 2;
                        
                        if (uidDataLength == 4 || uidDataLength == 7) {
                            byte[] uidData = Arrays.copyOfRange(uidResponse, 0, uidDataLength);
                            String uidHex = bytesToHex(uidData);
                            cardData.put("cardUID", uidHex);
                            cardData.put("uidLength", uidDataLength);
                            Log.d(TAG, "✅ UID: " + uidHex + " (" + uidDataLength + " bytes)");
                        } else {
                            Log.w(TAG, "⚠️ UID length unexpected: " + uidDataLength);
                        }
                    }
                    
                    // 4. GET CARD INFO
                    Log.d(TAG, "Step 4: GET CARD INFO");
                    String infoCmdHex = "00B300003F"; // GANTI DENGAN HEX ASLI
                    byte[] infoResponse = sendAPDUCommand(isoDep, infoCmdHex);
                    
                    if (isSuccessAPDU(infoResponse)) {
                        // Response: data 63 byte + 9000
                        int infoDataLength = infoResponse.length - 2;
                        
                        if (infoDataLength >= 63) {
                            byte[] infoData = Arrays.copyOfRange(infoResponse, 0, 63);
                            cardData.put("cardInfo", bytesToHex(infoData));
                            cardData.put("infoLength", infoDataLength);
                            Log.d(TAG, "✅ Card info: " + bytesToHex(infoData).substring(0, 32) + "...");
                        } else {
                            Log.w(TAG, "⚠️ Info data shorter: " + infoDataLength);
                        }
                    }
                    
                    // 5. GET LAST BALANCE
                    Log.d(TAG, "Step 5: GET LAST BALANCE");
                    String balanceCmdHex = "00B500000A"; // GANTI DENGAN HEX ASLI
                    byte[] balanceResponse = sendAPDUCommand(isoDep, balanceCmdHex);
                    
                    if (isSuccessAPDU(balanceResponse)) {
                        // Response: data 10 byte (old) / 4 byte (new) + 9000
                        int balanceDataLength = balanceResponse.length - 2;
                        
                        cardData.put("balanceResponseLength", balanceDataLength);
                        
                        if (balanceDataLength == 4) {
                            // NEW APPLET: 4 byte, first 4 byte in little endian is balance
                            int balance = ((balanceResponse[0] & 0xFF) << 0) |
                                         ((balanceResponse[1] & 0xFF) << 8) |
                                         ((balanceResponse[2] & 0xFF) << 16) |
                                         ((balanceResponse[3] & 0xFF) << 24);
                            
                            cardData.put("appletType", "NEW");
                            cardData.put("balance", balance);
                            cardData.put("balanceFormatted", "Rp " + balance);
                            cardData.put("balanceBytes", bytesToHex(Arrays.copyOfRange(balanceResponse, 0, 4)));
                            Log.d(TAG, "✅ New applet balance: Rp " + balance);
                            
                        } else if (balanceDataLength == 10) {
                            // OLD APPLET: 10 byte data
                            cardData.put("appletType", "OLD");
                            cardData.put("balanceBytes", bytesToHex(Arrays.copyOfRange(balanceResponse, 0, 10)));
                            Log.d(TAG, "✅ Old applet balance data (10 bytes)");
                            
                            // Jika balance ada di posisi tertentu (sesuai spec)
                            // Contoh: byte 0-3 adalah balance little endian
                            int balance = ((balanceResponse[0] & 0xFF) << 0) |
                                         ((balanceResponse[1] & 0xFF) << 8) |
                                         ((balanceResponse[2] & 0xFF) << 16) |
                                         ((balanceResponse[3] & 0xFF) << 24);
                            cardData.put("possibleBalance", balance);
                        } else {
                            Log.w(TAG, "⚠️ Unexpected balance length: " + balanceDataLength);
                        }
                    }
                    
                    // 🎯 KIRIM DATA KE JAVASCRIPT
                    sendDataToJavaScript(cardData);
                    
                } else {
                    Log.e(TAG, "❌ SELECT failed");
                    sendErrorToJS("SELECT_APPLET_FAILED", bytesToHex(selectResponse));
                }
                
                isoDep.close();
                Log.d(TAG, "✅ Disconnected");
                
            } catch (Exception e) {
                Log.e(TAG, "❌ Error: " + e.getMessage());
                sendErrorToJS("PROCESSING_ERROR", e.getMessage());
            }
        }
    });
}

    // Tambah method ini di class NfcPlugin
    private byte[] sendAPDUCommand(IsoDep isoDep, String hexCommand) throws IOException {
        Log.d(TAG, "📤 Sending APDU: " + hexCommand);
    
        // Convert hex string to byte array
        byte[] command = hexStringToByteArray(hexCommand);
    
        // Send command
        byte[] response = isoDep.transceive(command);
    
        Log.d(TAG, "📥 Response: " + bytesToHex(response));
        return response;
    }
    
    // Tambah di class NfcPlugin
    private byte[] hexStringToByteArray(String s) {
        if (s == null || s.length() % 2 != 0) {
            return new byte[0];
        }
        byte[] data = new byte[s.length() / 2];
        for (int i = 0; i < s.length(); i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                             + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    private String bytesToHex(byte[] bytes) {
        if (bytes == null) return "null";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
    
    private void sendDataToJavaScript(JSONObject data) {
    if (ndefCallback == null) {
        Log.e(TAG, "❌ No JavaScript callback registered");
        return;
    }
    
    try {
        Log.d(TAG, "📤 Sending to JavaScript: " + data.toString());
        
        PluginResult result = new PluginResult(PluginResult.Status.OK, data);
        result.setKeepCallback(true);
        ndefCallback.sendPluginResult(result);
        
        Log.d(TAG, "✅ Data sent successfully");
        
    } catch (Exception e) {
        Log.e(TAG, "❌ Error sending to JavaScript", e);
    }
}

private void sendErrorToJS(String errorType, String details) {
    try {
        JSONObject error = new JSONObject();
        error.put("error", errorType);
        error.put("details", details);
        error.put("type", "LIVIN_ERROR");
        
        sendDataToJavaScript(error);
        
    } catch (Exception e) {
        Log.e(TAG, "Error creating error JSON", e);
    }
}
    
// 🚨 HELPER METHODS (tambah di class)
  private boolean isSuccessAPDU(byte[] response) {
    if (response == null || response.length < 2) {
        Log.d(TAG, "❌ Invalid APDU response");
        return false;
    }
    
    // Check SW1 SW2 = 90 00
    int sw1 = response[response.length - 2] & 0xFF;
    int sw2 = response[response.length - 1] & 0xFF;
    
    boolean success = (sw1 == 0x90) && (sw2 == 0x00);
    
    Log.d(TAG, "APDU Status: " + String.format("%02X%02X", sw1, sw2) + 
          " = " + (success ? "SUCCESS" : "FAILED"));
    
    return success;
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
