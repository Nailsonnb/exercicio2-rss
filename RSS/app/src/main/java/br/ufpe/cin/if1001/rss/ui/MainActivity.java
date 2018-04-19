package br.ufpe.cin.if1001.rss.ui;


import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Toast;

import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

import br.ufpe.cin.if1001.rss.R;
import br.ufpe.cin.if1001.rss.db.SQLiteRSSHelper;
import br.ufpe.cin.if1001.rss.db.RssProviderContract;
import br.ufpe.cin.if1001.rss.domain.ItemRSS;
import br.ufpe.cin.if1001.rss.services.DownloadViaServices;
import br.ufpe.cin.if1001.rss.util.NovaNoticia;
import br.ufpe.cin.if1001.rss.util.ParserRSS;

public class MainActivity extends Activity {

    private ListView conteudoRSS;
    private final String RSS_FEED = "http://rss.cnn.com/rss/edition.rss";
    private SQLiteRSSHelper db;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MyApplication.activityResumed();
        setContentView(R.layout.activity_main);
        db = SQLiteRSSHelper.getInstance(this);

        conteudoRSS = findViewById(R.id.conteudoRSS);
        SimpleCursorAdapter adapter =
                new SimpleCursorAdapter(
                        //contexto, como estamos acostumados
                        this,
                        //Layout XML de como se parecem os itens da lista
                        R.layout.item,
                        //Objeto do tipo Cursor, com os dados retornados do banco.
                        //Como ainda não fizemos nenhuma consulta, está nulo.
                        null,
                        //Mapeamento das colunas nos IDs do XML.
                        // Os dois arrays a seguir devem ter o mesmo tamanho
                        new String[]{SQLiteRSSHelper.ITEM_TITLE, SQLiteRSSHelper.ITEM_DATE},
                        new int[]{R.id.itemTitulo, R.id.itemData},
                        //Flags para determinar comportamento do adapter, pode deixar 0.
                        0
                );
        //Seta o adapter. Como o Cursor é null, ainda não aparece nada na tela.
        conteudoRSS.setAdapter(adapter);

        // permite filtrar conteudo pelo teclado virtual
        conteudoRSS.setTextFilterEnabled(true);

        //Complete a implementação deste método de forma que ao clicar, o link seja aberto no navegador e
        // a notícia seja marcada como lida no banco
        conteudoRSS.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                SimpleCursorAdapter adapter = (SimpleCursorAdapter) parent.getAdapter();
                Cursor mCursor = ((Cursor) adapter.getItem(position));
                //envia um sinal para o link ser aberto em algum navegador
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(mCursor.getString(mCursor.getColumnIndex(RssProviderContract.LINK))));
                startActivity(intent);
                //marca no banco de dados como "lido" a noticia
                db.markAsRead(mCursor.getString(mCursor.getColumnIndex(RssProviderContract.LINK)));
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        MyApplication.activityResumed();//flag com estilo
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        String linkfeed = preferences.getString("rssfeedlink", getResources().getString(R.string.rssfeed));
        Intent downloadViaService = new Intent(getApplicationContext(), DownloadViaServices.class);
        downloadViaService.putExtra("url",linkfeed);
        startService(downloadViaService);
        new MainActivity.ExibirFeed().execute();
        NotificationManagerCompat.from(getApplicationContext()).cancel(0);




    }

    @Override
    protected void onDestroy() {
        MyApplication.activityPaused();
        db.close();
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.mainmenu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.btn_Config:
                startActivity(new Intent(this, ConfigActivity.class));
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume(){
        super.onResume();
        MyApplication.activityResumed();
        //criando uma nova intent para usar no localbroacast
        IntentFilter myFilter = new IntentFilter("my-local-broadcast");
        //registrando o localbroadcast
        LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(myBroadcastReceiver,myFilter);

    }

    @Override
    protected void onPause(){
        super.onPause();
        MyApplication.activityPaused();//particialmente vivisivel talvez de um bug ficar de olho aki
        LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(myBroadcastReceiver);
    }
    //criando um broadcast receiver padrao
    BroadcastReceiver myBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            //exibe o feed quando o aplicativo e iniciado pela primeira vez no celular
            new MainActivity.ExibirFeed().execute();
            Toast.makeText(context, "feed atualizado com noticias novas!", Toast.LENGTH_LONG).show();
            Log.d("RSS","recebendo broadcast local");
            }
    };

    class ExibirFeed extends AsyncTask<Void, Void, Cursor> {

        @Override
        protected Cursor doInBackground(Void... voids) {
            Cursor c = db.getItems();
            c.getCount();
            return c;
        }

        @Override
        protected void onPostExecute(Cursor c) {
            if (c != null) {
                ((CursorAdapter) conteudoRSS.getAdapter()).changeCursor(c);
            }
        }
    }

}