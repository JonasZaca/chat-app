package moblab.exemplolista;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.PowerManager;
import android.os.StrictMode;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.format.Formatter;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import com.github.kittinunf.fuel.Fuel;
import com.github.kittinunf.fuel.core.FuelError;
import com.github.kittinunf.fuel.core.Handler;
import com.github.kittinunf.fuel.core.Request;
import com.github.kittinunf.fuel.core.Response;
import com.github.kittinunf.result.Result;
import com.moblab.tethering.GerenciaRedeD2D;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import kotlin.Triple;

public class MainActivity extends AppCompatActivity {

    public static ListView listaView; // Este e a lista de itens do layout.
    public static List<ItemListView> listaItensView; // Essa e a lista de itens que contem as mensagens.
    public static AdapterListView adaptador; // Essa e o adaptador da lista do layout.
    String nome = "SemNome"; // Essa variavel contem o nome do usuario.
    public final static String IP = "http://192.168.0.103:80";
    public static boolean INTERNET = true;
    public static List<String> msgsMC = new ArrayList<>();
    public ExecutorService pool = Executors.newFixedThreadPool(100);
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;
    private static final int PERMISSION_REQUEST_FINE_LOCATION = 2;

    public static String ENDERECO_MULTICAST = "225.5.6.7";

    private GerenciaRedeD2D gerenciaRedeD2D = null;
    public TaskReceberMSGServer taskReceberMSGServer = null;

    public void iniciarServerTethering() {

        Log.d("TaskReceberMSGServer", "INICIANDO STATUS SERVER");

        if (taskReceberMSGServer == null) {
            taskReceberMSGServer = new TaskReceberMSGServer(this);
            taskReceberMSGServer.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        } else {
            Log.d("TaskReceberMSGServer", "SERVER JÁ EXECUTANDO");
        }
    }

    public void atualizaLista(final String nome, final String mensagem) {

        MainActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ItemListView novoItem = new ItemListView(nome, mensagem);
                MainActivity.listaItensView.add(novoItem);

                //int index = listaView.getFirstVisiblePosition();
                //View v = listaView.getChildAt(0);
                //int top = (v == null) ? 0 : v.getTop();

                // Atualiza a listView
                adaptador = new AdapterListView(MainActivity.this, listaItensView);
                listaView.setAdapter(adaptador);
                listaView.setSelection(adaptador.getCount() - 1);
                // listaView.setSelectionFromTop(listaItensView.size(), top);

                                    /* Volta a visualizacao da lista para o itemView que ele estava visualizando antes de atualizar. */
                //listaView.setSelectionFromTop(index, top);
            }//public void run() {
        });
    }


    class EnviarMSG extends AsyncTask<String, String, List> {

        public String nom, msg;

        public EnviarMSG(String nome, String msg) {
            this.nom = nome;
            this.msg = msg;
        }

        @Override
        protected List<ItemListView> doInBackground(String... params) {

            Fuel.get(IP + "/adicionar?nome=" + nom + "&mensagem=" + msg).timeout(1000).responseString(new Handler<String>() {

                // SEM CONEXAO COM INTERNET, VERIFICA CONEXÃO COM WIFI LOCAL
                @Override
                public void failure(Request request, Response response, FuelError error) {

                    INTERNET = false;

                    Log.d("ENV", "FALHA");
                    // Nesse caso teremos que verificar se existe conexão WIFI e abrir multicast.
                    String mensg = nom + "656789" + msg;

                    Log.d("MAINACTIVITY", "ENTREI AQUI");
                    for (int i= 2; i <= 253; i++) {
                        pool.execute(new EnvMSGSep(5555, getIPRede(i), mensg));
                    }
                    Toast.makeText(MainActivity.this, "Mensagem Enviada pela Rede Local!", Toast.LENGTH_SHORT).show();
                }

                // CONEXAO COM INTERNET OK
                @Override
                public void success(Request request, Response response, String data) {
                    Log.d("ENV", "SUCESSO");
                    INTERNET = true;
                    Toast.makeText(MainActivity.this, "Mensagem Enviada pela Internet!", Toast.LENGTH_SHORT).show();
                }
            });

            return null;
        }
    }

    public String getMyIP() {
        WifiManager wifiManager = (WifiManager) MainActivity.this.getSystemService(Context.WIFI_SERVICE);
        int ips = wifiManager.getConnectionInfo().getIpAddress();
        String ipAddress = Formatter.formatIpAddress(ips);
        return ipAddress;
    }

    public String getServerIP() {
        WifiManager wifiManager = (WifiManager) MainActivity.this.getSystemService(Context.WIFI_SERVICE);
        int ips = wifiManager.getConnectionInfo().getIpAddress();
        @SuppressWarnings("deprecation")
        String ipAddress = Formatter.formatIpAddress(ips);
        return ipAddress.substring(0, ipAddress.lastIndexOf(".") + 1) + "1";
    }

    public String getIPRede(int ip) {
        WifiManager wifiManager = (WifiManager) MainActivity.this.getSystemService(Context.WIFI_SERVICE);
        int ips = wifiManager.getConnectionInfo().getIpAddress();
        @SuppressWarnings("deprecation")
        String ipAddress = Formatter.formatIpAddress(ips);
        return ipAddress.substring(0, ipAddress.lastIndexOf(".")+1) + String.valueOf(ip);
    }

    // Esse metodo adiciona uma nova mensagem. Para isso ele pega o texto e envia
    // ao webservice solicitando uma adicao de nova mensagem.
    public void adicionarItem(View botao) {

        if (nome.isEmpty()) {
            Toast.makeText(MainActivity.this, "Você deve definir um nome antes de enviar mensagens!", Toast.LENGTH_SHORT).show();
            obterNome();
        } else {
            EditText campoTexto = (EditText) findViewById(R.id.editText);
            String textoItem = campoTexto.getText().toString();

            ItemListView itemLista = new ItemListView();
            itemLista.setTexto(textoItem);

            String mensagem = textoItem;
            campoTexto.setText("");
            ((EditText) findViewById(R.id.editText)).setHint("Mensagem");

            Log.d("ENV", "START TRHEAD");


            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                new EnviarMSG(nome, mensagem).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            } else {
                new EnviarMSG(nome, mensagem).execute();
            }
        }
    }

    // Metodo inicial da aplicacao. Tudo comeca aqui. Configuracao do layout inicial e
    // chamada do metodo para configurar o nome, e configuracao da lista de mensagens.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checkSystemWritePermission();

        if (android.os.Build.VERSION.SDK_INT > 9) {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);
        }

        obterNome();

        this.listaView = (ListView) findViewById(R.id.lista_itens);

        this.listaItensView = new ArrayList<ItemListView>();

        this.adaptador = new AdapterListView(this, this.listaItensView);

        this.listaView.setAdapter(this.adaptador);

        ((EditText) findViewById(R.id.editText)).setHint("Mensagem");

        checarPermissoes();

        new ObterMensagensTask().execute();

        gerenciaRedeD2D = new GerenciaRedeD2D(MainActivity.this);
        gerenciaRedeD2D.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

        iniciarServerTethering();
    }

    public void checarPermissoes() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Este app precisa de acesso à localização");
                builder.setMessage("Por favor, conceda acesso à localização de modo que este app possa obter sua localização.");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @TargetApi(Build.VERSION_CODES.M)
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
                    }
                });
                builder.show();
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (this.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Este app precisa de acesso ao WiFi");
                builder.setMessage("Por favor, conceda acesso à localização de modo que este app possa detectar dispositivos na proximidade.");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @TargetApi(Build.VERSION_CODES.M)
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_FINE_LOCATION);
                    }
                });
                builder.show();
            }
        }
    }

    // Cria um dialogo para o usuario setar no nome dele na aplicacao.
    public void obterNome() {

        SharedPreferences sharedPref = getSharedPreferences("TextWIn", Context.MODE_PRIVATE);
        String retrievedString = sharedPref.getString("nome", "SemNome");

        if (retrievedString.equalsIgnoreCase("SemNome")) {

            final EditText txtUrl = new EditText(this);

            new AlertDialog.Builder(this)
                    .setTitle("Defina seu nome de usuário")
                    .setView(txtUrl)
                    .setPositiveButton("Salvar", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            nome = txtUrl.getText().toString();
                            SharedPreferences sharedPref = getSharedPreferences("TextWIn", Context.MODE_PRIVATE);
                            SharedPreferences.Editor editor = sharedPref.edit();
                            editor.putString("nome", nome);
                            editor.commit();
                        }
                    })
                    .show();
        } else {
            nome = retrievedString;
            Toast.makeText(MainActivity.this, "Bem Vindo ao Chat " + nome, Toast.LENGTH_SHORT).show();
        }
    }


    // Essa AsyncTask e uma Thread que roda em background na aplicacao. O loop dela e infinito
    // e fica atualizando lista de mensagens solicitando o webservice.
    class ObterMensagensTask extends AsyncTask<String, String, List> {

        public boolean continuar = true;

        @Override
        protected List<ItemListView> doInBackground(String... params) {

            Log.d("ENTROU", "OBTER FUEL");

            WifiManager wifiManager = (WifiManager) MainActivity.this.getSystemService(Context.WIFI_SERVICE);

            WifiManager.MulticastLock multicastLock = wifiManager.createMulticastLock("multicast.test");
            multicastLock.setReferenceCounted(true);
            multicastLock.acquire();


            multicastLock.release();

            while (continuar) {

                try {
                    Triple<Request, Response, Result<byte[], FuelError>> data = Fuel.get(IP + "/mensagens").timeout(500).response();
                    Request request = data.getFirst();
                    Response response = data.getSecond();
                    Result<byte[], FuelError> text = data.getThird();
                    Log.d("RESPOSTA 1", text.toString());
                    INTERNET = true;

                    byte data2[] = text.component1();
                    int i;
                    for (i = 0; i < data2.length; i++) {
                        if (data2[i] == '\0')
                            break;
                    }

                    // Cria uma nova lista e adiciona todos os itens baixados que estao no data para o listaItensView.
                    try {
                        JSONArray jsonarray = new JSONArray(new String(data2, 0, i, "UTF-8"));
                        for (i = 0; i < jsonarray.length(); i++) {
                            JSONObject jsonobject = jsonarray.getJSONObject(i);
                            String nome = jsonobject.getString("nome");
                            String mensagem = jsonobject.getString("mensagem");

                            String msg = nome + mensagem;

                            if (!msgsMC.contains(msg)) {
                                msgsMC.add(msg);

                                ItemListView novoItem = new ItemListView(nome, mensagem);
                                listaItensView.add(novoItem);

                                MainActivity.this.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        //int index = listaView.getFirstVisiblePosition();
                                        //View v = listaView.getChildAt(0);
                                        //int top = (v == null) ? 0 : v.getTop();

                                        // Atualiza a listView
                                        adaptador = new AdapterListView(MainActivity.this, listaItensView);
                                        listaView.setAdapter(adaptador);
                                        // listaView.setSelectionFromTop(listaItensView.size(), top);
                                        listaView.setSelection(adaptador.getCount() - 1);
                                    /* Volta a visualizacao da lista para o itemView que ele estava visualizando antes de atualizar. */
                                        //listaView.setSelectionFromTop(index, top);
                                    }//public void run() {
                                });
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                } catch (Exception networkError) {
                    Log.d("FALHOU", "OBTER FUEL");
                    INTERNET = false;
                }
                try {
                    Thread.sleep(2000);
                } catch (Exception e) {

                }
            }

            return null;
        }
    }

    private boolean checkSystemWritePermission() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1) {
            String pkg = getPackageName();
            PowerManager pm = getSystemService(PowerManager.class);

            if (!pm.isIgnoringBatteryOptimizations(pkg)) {
                Intent i =
                        new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                .setData(Uri.parse("package:" + pkg));

                startActivity(i);
            }
        }

        boolean retVal = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            retVal = Settings.System.canWrite(this);
            if (!retVal) {
                MainActivity.this.runOnUiThread(new Runnable() {
                    public void run() {
                        Toast.makeText(MainActivity.this, "Permita a Aplicação Alterar o Estado da Sua Rede!", Toast.LENGTH_LONG).show();
                    }
                });

                Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                intent.setData(Uri.parse("package:" + MainActivity.this.getPackageName()));
                startActivity(intent);
            }
        }
        return retVal;
    }

    class EnvMSGSep implements Runnable {
        int porta = 5555;
        String IP = "";
        String mensagem = "";
        ConexaoCliente conexao = null;

        EnvMSGSep(int porta, String IP, String mensagem) {
            this.porta = porta;
            this.IP = IP;
            this.mensagem = mensagem;
            Log.d("TaskEnviarMSGServer", this.IP + " - " + this.porta + " - " + this.mensagem);
        }

        public void run() {
            conexao = new ConexaoCliente(this.IP, this.porta);

            if (conexao.conectaServidor()) {
                Log.d("TaskEnviarMSGServer", "CONECTADO AO SERVER");
                try {
                    conexao.getEnviaDados().writeObject(this.mensagem);
                    conexao.getEnviaDados().flush();
                    Log.d("TaskEnviarMSGServer", "enviado mensagem");
                    conexao.desconectaServidor();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    class TrataMSGRec implements Runnable {
        DatagramPacket pacote = null;

        TrataMSGRec(DatagramPacket pacote) {
            this.pacote = pacote;
        }

        public void run() {
            try {
                Log.d("TRATANDO", "MENSAGEM");
                byte data[] = pacote.getData();
                int i;
                for (i = 0; i < data.length; i++) {
                    if (data[i] == '\0')
                        break;
                }

                String msg;
                Log.d("TESTE", "FALHA 3");

                msg = new String(data, 0, i, "UTF-8");

                Log.d("RECEBEU", msg);
                String[] dados = msg.split("656789");

                String msgMCNew = dados[0] + dados[1];
                Log.d("TESTE", "FALHA 4");

                if (!msgsMC.contains(msgMCNew)) {
                    msgsMC.add(msgMCNew);

                    ItemListView novoItem = new ItemListView(dados[0], dados[1]);
                    listaItensView.add(novoItem);

                    MainActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            int index = listaView.getFirstVisiblePosition();
                            View v = listaView.getChildAt(0);
                            int top = (v == null) ? 0 : v.getTop();

                            // Atualiza a listView
                            adaptador = new AdapterListView(MainActivity.this, listaItensView);
                            listaView.setAdapter(adaptador);
                            // listaView.setSelectionFromTop(listaItensView.size(), top);

                                    /* Volta a visualizacao da lista para o itemView que ele estava visualizando antes de atualizar. */
                            listaView.setSelection(adaptador.getCount() - 1);
                            //listaView.setSelectionFromTop(index, top);
                        }//public void run() {
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
