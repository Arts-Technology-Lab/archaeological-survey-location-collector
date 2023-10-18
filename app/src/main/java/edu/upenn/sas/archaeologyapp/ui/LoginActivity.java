package edu.upenn.sas.archaeologyapp.ui;


import static edu.upenn.sas.archaeologyapp.util.StaticSingletons.getRequestQueueSingleton;
import static edu.upenn.sas.archaeologyapp.services.UserAuthentication.tryLogin;
import edu.upenn.sas.archaeologyapp.util.ExtraUtils.InjectableFunc;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.Toast;

import com.android.volley.RequestQueue;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import edu.upenn.sas.archaeologyapp.R;

/**
 * This activity governs the login mechanism
 */

public class LoginActivity extends BaseActivity{
    Context context = this;
    RequestQueue queue ;

    /**
     * When login is successful, we change the page to mainActivity.
     */
    private InjectableFunc handleLoginSuccess = () -> {
        Toast.makeText(context, "Login successful", Toast.LENGTH_SHORT).show();
        LoginActivity.super.startActivityUsingIntent(MainActivity.class);
    };
    /**
     * When login is unsuccessful, we show that the username and/or password is wrong.
     */
    private InjectableFunc handleLoginFailure = () -> {
        Toast.makeText(context, "Wrong username and/or password ", Toast.LENGTH_SHORT).show();
    };

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {

        queue =  getRequestQueueSingleton(getApplicationContext());;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

//      This part of the code
        String [] temp= new String[]{"j20200007.kotsf.com", "google.com"};
        ArrayAdapter arrayAdapter = new ArrayAdapter(context, R.layout.listitem,temp );
        AutoCompleteTextView editText =  findViewById(R.id.bbbbbb);
        editText.setText(temp[0], false);
        //Here get the saved
        editText.setAdapter(arrayAdapter);
        editText.addTextChangedListener(new TextWatcher() {

            public void afterTextChanged(Editable s) {
                System.out.println(s);
                // you can call or do what you want with your EditText here

                // yourEditText...
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });
        findViewById(R.id.loginButton).setOnClickListener(view -> {
            TextInputEditText userName = (findViewById(R.id.userName));

            EditText userPassword = (findViewById(R.id.userPassword));
            String userNameStr = (userName.getText().toString());
            String userPasswordStr =   (userPassword.getText().toString());
            if (userNameStr != null && !userNameStr.isEmpty() && userPasswordStr !=null && !userPasswordStr.isEmpty()){
                tryLogin(userNameStr, userPasswordStr, context, handleLoginSuccess, handleLoginFailure, queue);

            }else{
                Toast.makeText(LoginActivity.this, "Both fields cannot be empty", Toast.LENGTH_SHORT).show();
            }
        });
    }



}
