package cs173.colorfindr;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class MainActivity extends AppCompatActivity {

    private Button playGame;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

//        playGame = (Button) findViewById(R.id.playbutton);
//
//        playGame.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                Intent mainIntent = new Intent(MainActivity.this, GameActivity.class);
//                startActivity(mainIntent);
//
//            }
//        });
    }
}
