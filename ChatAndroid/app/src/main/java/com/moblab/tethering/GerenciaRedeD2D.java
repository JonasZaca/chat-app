package com.moblab.tethering;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.util.Log;
import android.widget.Toast;

import com.github.kittinunf.fuel.Fuel;
import com.github.kittinunf.fuel.core.FuelError;
import com.github.kittinunf.fuel.core.Request;
import com.github.kittinunf.fuel.core.Response;
import com.github.kittinunf.result.Result;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import kotlin.Triple;
import moblab.exemplolista.ItemListView;
import moblab.exemplolista.MainActivity;
import moblab.exemplolista.TaskReceberMSGServer;


// Essa AsyncTask e uma Thread que roda em background na aplicacao. O loop dela e infinito
// e fica atualizando lista de mensagens solicitando o webservice.
public class GerenciaRedeD2D extends AsyncTask<String, String, List> {

    public boolean continuar = true;
    public int tempo_cliente = 0;
    public WifiApManager TetheringManager = null;
    public WifiManager wifiManager = null;
    public int netID = -8888;
    public MainActivity app = null;
    public static String SSID_WIFI_LOCAL = "TextWIn"; // Nome da Rede D2D
    public static String PSK_WIFI_LOCAL = "123456789"; // Senha da Rede D2D
    public static boolean iTethering = false;
    public static String redeAtual = "";

    public GerenciaRedeD2D(MainActivity contexto) {
        this.app = contexto;
        Log.d("D2D", "CRIANDO");
    }

    public static int randInt(int min, int max) {
        Random foo = new Random();
        int randomNumber = foo.nextInt(max - min) + min;
        if(randomNumber == min) {
            return min + 1;
        }
        else {
            return randomNumber;
        }

    }

    @Override
    protected List<ItemListView> doInBackground(String... params) {

        Log.d("D2D", "ENTROU");

        // A AsyncTask fica sempre executando. Posteriormente, essa Thread terá que ser um Service.
        while (continuar) {

            //Log.d("D2D", "INTERNET = " + String.valueOf(MainActivity.INTERNET));

            // Verifica primeiro se o dispositivo tem acesso a internet.
            if (!MainActivity.INTERNET) {

                tempo_cliente = ((100 - (int) getBateria()) + randInt(10, 50)) * 250;
                long startTime = System.currentTimeMillis();

                Log.d("D2D", "TEMPO CLIENTE - " + String.valueOf(tempo_cliente));

                // Começa a Buscar as redes TextWin de acordo com o tempo gerado pelo nivel de bateria.
                while ((System.currentTimeMillis() - startTime) < tempo_cliente) {

                   // Log.d("D2D", "CLIENTE PESQUISANDO - " + String.valueOf(System.currentTimeMillis() - startTime));

                    if (wifiManager == null)
                        wifiManager = (WifiManager) app.getSystemService(Context.WIFI_SERVICE);

                    if (!wifiManager.isWifiEnabled())
                        wifiManager.setWifiEnabled(true);

                    WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                    redeAtual = (wifiInfo.getSSID()).toString();

                   // Log.d("D2D", redeAtual);

                    if (redeAtual != null) {
                        if (redeAtual.equalsIgnoreCase("<unknown ssid>") || redeAtual.equalsIgnoreCase("") || redeAtual.equalsIgnoreCase("0x")) {

                           // Log.d("D2D", "CLIENTE NAO ENCONTRADO WIFI - START SCAN");

                            wifiManager.startScan();

                            dormir(2000);

                            List<ScanResult> apList = wifiManager.getScanResults();

                            WifiConfiguration tmpConfig = null;

                            // Procura uma rede TextWIn dentro das redes wifi escaneadas.
                            for (ScanResult result : apList) {
                                if (result.SSID.contains(SSID_WIFI_LOCAL)) {
                                    Log.d("TEXTWIN", "ENCONTROU A REDE - " + result.SSID);
                                    tmpConfig = new WifiConfiguration();
                                    tmpConfig.BSSID = result.BSSID;
                                    tmpConfig.SSID = "\"" + result.SSID + "\"";
                                    tmpConfig.priority = 1;
                                    tmpConfig.preSharedKey = "\"" + PSK_WIFI_LOCAL + "\"";
                                    tmpConfig.status = WifiConfiguration.Status.ENABLED;
                                    tmpConfig.hiddenSSID = false;
                                    tmpConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
                                    tmpConfig.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
                                    tmpConfig.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
                                }
                            }

                            // Se encontrar uma rede TextWIn, se conecta a ela.
                            if (tmpConfig != null) {
                                netID = wifiManager.addNetwork(tmpConfig);

                                if (wifiManager.enableNetwork(netID, true)) {
                                    redeAtual = tmpConfig.SSID;

                                    if (redeAtual.contains("TextWIn")) {
                                        this.app.runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                app.getSupportActionBar().setTitle("TextWIn [STA - D2D]");
                                                app.iniciaObterClientes();
                                            }
                                        });

                                    }
                                    else {
                                        this.app.runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                app.getSupportActionBar().setTitle("TextWIn [STA - " + redeAtual + "]");
                                                app.iniciaObterClientes();
                                            }
                                        });
                                    }

                                    iTethering = false;
                                }
                                dormir(2000);
                            }

                        }
                        // Caso o dispositivo esteja conectado a uma rede wifi ou ao Tethering, continua como cliente.
                        else {
                            iTethering = false;

                            if (app.INTERNET) {
                                if (redeAtual != null) {
                                    this.app.runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            app.getSupportActionBar().setTitle("TextWIn [STA - SERVER]");
                                        }
                                    });
                                }
                            }
                            else {

                                if (redeAtual.contains("TextWIn")) {
                                    this.app.runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            app.getSupportActionBar().setTitle("TextWIn [STA - D2D]");
                                        }
                                    });

                                }
                                else {
                                    this.app.runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            app.getSupportActionBar().setTitle("TextWIn [STA - " + redeAtual + "]");
                                        }
                                    });
                                }
                            }

                            dormir(1000);
                            startTime = System.currentTimeMillis();
                        }
                    }
                }

                Log.d("D2D", "HABILITANDO TETHERING WIFI");
                redeAtual = "";

                if (netID != -8888) {
                    wifiManager.removeNetwork(netID);
                    netID = -8888;
                }

                // Estourou o tempo de busca de redes, neste caso o tethering é ativado.
                tempo_cliente = ((100 - (int) getBateria()) + randInt(10, 50)) * 1000;
                startTime = System.currentTimeMillis();

                while ((System.currentTimeMillis() - startTime) < tempo_cliente) {

                    if (TetheringManager == null)
                        TetheringManager = new WifiApManager(app);

                    if (!TetheringManager.isWifiApEnabled() && !iTethering) {
                        TetheringManager.setWifiApEnabled(null, true);


                       // Log.d("D2DAP", "WIFI HABILITADO");
                        this.app.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                app.getSupportActionBar().setTitle("TextWIn [AP - D2D]");
                                app.iniciaObterClientes();
                            }
                        });

                        iTethering = true;
                        dormir(2000);
                    }

                    // Busca os clientes conectados ao Tethering.
                    if (TetheringManager.isWifiApEnabled()) {
                        final ArrayList<ClientScanResult> clientsAP = getClientsTethering();

                        // Se houver clientes conectados, continua como Tethering.
                        if (clientsAP != null && !clientsAP.isEmpty()) {
                            iTethering = true;
                            this.app.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    app.getSupportActionBar().setTitle("TextWIn [AP - D2D]");
                                }
                            });
                            dormir(1000);
                            startTime = System.currentTimeMillis();
                        }
                    }
                }
                TetheringManager.setWifiApEnabled(null, false);

                iTethering = false;
            }
            else {
                if (redeAtual != null) {
                    if (redeAtual.equals("")) {
                        this.app.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                app.getSupportActionBar().setTitle("TextWIn [STA - OFFLINE]");
                            }
                        });
                    }
                    else if (redeAtual.contains("TextWIn")) {
                        this.app.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                app.getSupportActionBar().setTitle("TextWIn [STA - D2D]");
                            }
                        });

                    }
                    else {
                        this.app.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                app.getSupportActionBar().setTitle("TextWIn [STA - " + redeAtual + "]");
                            }
                        });
                    }
                }
                dormir(2000);
            }
        }

        return null;
    }

    public void dormir(int tempo) {
        try {
            Thread.sleep(tempo);
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    public ArrayList<ClientScanResult> getClientsTethering() {
        ArrayList<ClientScanResult> result = new ArrayList<ClientScanResult>();
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader("/proc/net/arp"));
            String line;
            while ((line = br.readLine()) != null) {
                String[] splitted = line.split(" +");

                if ((splitted != null) && (splitted.length >= 4)) {
                    String mac = splitted[3];
                    if (mac.matches("..:..:..:..:..:..")) {
                        boolean isReachable = InetAddress.getByName(splitted[0]).isReachable(300);

                        if (isReachable) {
                            result.add(new ClientScanResult(splitted[0], splitted[3], splitted[5], isReachable));
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(this.getClass().toString(), e.toString());
        } finally {
            try {
                br.close();
            } catch (IOException e) {
                Log.e(this.getClass().toString(), e.getMessage());
            }
        }
        return result;
    }

    public float getBateria() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = app.registerReceiver(null, ifilter);
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        return (level / (float) scale) * 100;
    }
}
