package de.thecode.android.tazreader.start;


import android.app.Fragment;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.google.common.base.Strings;

import java.util.HashMap;
import java.util.Map;

import de.thecode.android.tazreader.R;
import de.thecode.android.tazreader.dialog.TcDialog;
import de.thecode.android.tazreader.dialog.TcDialogIndeterminateProgress;
import de.thecode.android.tazreader.secure.Base64;
import de.thecode.android.tazreader.sync.AccountHelper;
import de.thecode.android.tazreader.utils.BaseFragment;
import de.thecode.android.tazreader.volley.TazStringRequest;
import de.thecode.android.tazreader.volley.VolleySingleton;

import static android.graphics.PorterDuff.Mode.SRC_ATOP;

/**
 * A simple {@link Fragment} subclass.
 */
public class LoginFragment extends BaseFragment {

    public static final String DIALOG_CHECK_CREDENTIALS = "checkCrd";
    public static final String DIALOG_ERROR_CREDENTIALS = "errorCrd";
    private EditText editUser;
    private EditText editPass;
    private Button loginButton;
    private Button orderButton;
    private IStartCallback callback;

    public LoginFragment() {
        // Required empty public constructor
    }


    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        callback = (IStartCallback) getActivity();
        callback.onUpdateDrawer(this);

        View view = inflater.inflate(R.layout.start_login, container, false);
        loginButton = (Button) view.findViewById(R.id.buttonLogin);
        orderButton = (Button) view.findViewById(R.id.buttonOrder);
        orderButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(getString(R.string.abo_url)));
                startActivity(i);
            }
        });

        orderButton.getBackground()
                   .setColorFilter(getResources().getColor(R.color.start_login_calltoaction_button_background), SRC_ATOP);
        editUser = (EditText) view.findViewById(R.id.editUser);
        editPass = (EditText) view.findViewById(R.id.editPass);
        editPass.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if ((event.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_ENTER)) {
                    checkLogin();
                    return true;
                }
                return false;
            }
        });

        if (callback.getAccountHelper()
                    .isAuthenticated()) {
            setUiForLoggedIn();
            editUser.setText(callback.getAccountHelper()
                                     .getUser());
            editPass.setText(callback.getAccountHelper()
                                     .getPassword());
        } else setUiForNotLoggedIn();

        return view;
    }

    private void blockUi() {
        new TcDialogIndeterminateProgress().withCancelable(false)
                                                          .withMessage(R.string.dialog_check_credentials)
                                                          .show(getFragmentManager(), DIALOG_CHECK_CREDENTIALS);
        editUser.setEnabled(false);
        editPass.setEnabled(false);
    }

    private void unblockUi() {
        editUser.setEnabled(true);
        editPass.setEnabled(true);
        TcDialogIndeterminateProgress.dismissDialog(getFragmentManager(), DIALOG_CHECK_CREDENTIALS);
    }

    private void setUiForLoggedIn() {
        editUser.setEnabled(false);
        editPass.setEnabled(false);
        loginButton.setText(R.string.string_deleteAccount_button);
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                callback.getAccountHelper()
                        .setUser(AccountHelper.ACCOUNT_DEMO_USER);
                callback.getAccountHelper()
                        .setPassword(AccountHelper.ACCOUNT_DEMO_PASS);
                callback.getAccountHelper()
                        .setAuthenticated(false);
                setUiForNotLoggedIn();
                callback.logoutFinished();
            }
        });
        orderButton.setVisibility(View.INVISIBLE);
    }

    private void setUiForNotLoggedIn() {
        editUser.setEnabled(true);
        editPass.setEnabled(true);
        loginButton.setText(R.string.string_login_button);
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkLogin();
            }
        });
        orderButton.setVisibility(View.VISIBLE);
    }

    private void checkLogin() {
        if (Strings.isNullOrEmpty(editUser.getText()
                                          .toString()) || Strings.isNullOrEmpty(editPass.getText()
                                                                                        .toString())) {
            new TcDialog().withIcon(R.drawable.ic_alerts_and_states_warning)
                                         .withTitle(R.string.dialog_error_title)
                                         .withMessage(R.string.dialog_error_no_credentials)
                                         .withPositiveButton()
                                         .show(getFragmentManager(), DIALOG_ERROR_CREDENTIALS);
            return;
        }
        if (AccountHelper.ACCOUNT_DEMO_USER.equalsIgnoreCase(editUser.getText()
                                                                     .toString())) {
            new TcDialog().withIcon(R.drawable.ic_alerts_and_states_warning)
                          .withTitle(R.string.dialog_error_title)
                          .withMessage(R.string.dialog_error_credentials_not_allowed)
                          .withPositiveButton()
                          .show(getFragmentManager(), DIALOG_ERROR_CREDENTIALS);
            return;
        }


        blockUi();

        Response.Listener<String> responseListener = new Response.Listener<String>() {

            @Override
            public void onResponse(String response) {
                unblockUi();
                callback.getAccountHelper()
                        .setUser(editUser.getText()
                                         .toString());
                callback.getAccountHelper()
                        .setPassword(editPass.getText()
                                             .toString());
                callback.getAccountHelper()
                        .setAuthenticated(true);
                setUiForLoggedIn();
                callback.loginFinished();
            }
        };

        TazStringRequest.MyStringErrorListener errorListener = new TazStringRequest.MyStringErrorListener() {

            @Override
            public void onErrorResponse(VolleyError error, final String string) {

                new TcDialog().withIcon(R.drawable.ic_alerts_and_states_warning)
                                             .withTitle(R.string.dialog_error_title)
                                             .withMessage(string)
                                             .withPositiveButton()
                                             .show(getFragmentManager(), DIALOG_ERROR_CREDENTIALS);
                unblockUi();
                setUiForNotLoggedIn();
            }
        };

        TazStringRequest stringRequest = new TazStringRequest(Request.Method.GET, getString(R.string.checkLogin), responseListener, errorListener) {

            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headerMap = new HashMap<String, String>();
                String credentials = editUser.getText()
                                             .toString() + ":" + editPass.getText()
                                                                         .toString();
                String base64EncodedCredentials = Base64.encodeToString(credentials.getBytes(), Base64.NO_WRAP);
                headerMap.put("Authorization", "Basic " + base64EncodedCredentials);
                return headerMap;
            }
        };

        VolleySingleton.getInstance(getActivity())
                       .addToRequestQueue(stringRequest);
    }


}