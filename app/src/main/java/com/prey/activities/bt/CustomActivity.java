package com.prey.activities.bt;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;

import com.braintreepayments.api.exceptions.InvalidArgumentException;
import com.braintreepayments.api.interfaces.BraintreeErrorListener;
import com.braintreepayments.api.interfaces.BraintreeResponseListener;
import com.braintreepayments.api.interfaces.ConfigurationListener;
import com.braintreepayments.api.interfaces.PaymentMethodNonceCreatedListener;
import com.braintreepayments.api.interfaces.UnionPayListener;

import com.braintreepayments.api.AndroidPay;
import com.braintreepayments.api.exceptions.InvalidArgumentException;
import com.braintreepayments.api.models.CardBuilder;
import com.braintreepayments.api.models.Configuration;

import java.util.ArrayList;
import java.util.Collections;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

/**
 * Created by oso on 23-11-16.
 */

public class CustomActivity extends BaseActivity implements ConfigurationListener, UnionPayListener,
        PaymentMethodNonceCreatedListener, BraintreeErrorListener, OnCardFormSubmitListener,
        OnCardFormFieldFocusedListener {

    private static final String EXTRA_UNIONPAY = "com.braintreepayments.demo.EXTRA_UNIONPAY";
    private static final String EXTRA_UNIONPAY_ENROLLMENT_ID = "com.braintreepayments.demo.EXTRA_UNIONPAY_ENROLLMENT_ID";

    private Configuration mConfiguration;
    private Cart mCart;
    private String mDeviceData;
    private boolean mIsUnionPay;
    private String mEnrollmentId;

    private ImageButton mPayPalButton;
    private ImageButton mAndroidPayButton;
    private CardForm mCardForm;
    private TextInputLayout mSmsCodeContainer;
    private EditText mSmsCode;
    private Button mSendSmsButton;
    private Button mPurchaseButton;

    private CardType mCardType;

    @Override
    protected void onCreate(Bundle onSaveInstanceState) {
        super.onCreate(onSaveInstanceState);

        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.custom_activity);
        setUpAsBack();

        mCart = getIntent().getParcelableExtra(MainActivity.EXTRA_ANDROID_PAY_CART);

        mPayPalButton = (ImageButton) findViewById(R.id.paypal_button);
        mAndroidPayButton = (ImageButton) findViewById(R.id.android_pay_button);

        mCardForm = (CardForm) findViewById(R.id.card_form);
        mCardForm.setOnFormFieldFocusedListener(this);
        mCardForm.setOnCardFormSubmitListener(this);

        mSmsCodeContainer = (TextInputLayout) findViewById(R.id.sms_code_container);
        mSmsCode = (EditText) findViewById(R.id.sms_code);
        mSendSmsButton = (Button) findViewById(R.id.unionpay_enroll_button);
        mPurchaseButton = (Button) findViewById(R.id.purchase_button);

        if (onSaveInstanceState != null) {
            mIsUnionPay = onSaveInstanceState.getBoolean(EXTRA_UNIONPAY);
            mEnrollmentId = onSaveInstanceState.getString(EXTRA_UNIONPAY_ENROLLMENT_ID);

            if (mIsUnionPay) {
                mSendSmsButton.setVisibility(VISIBLE);
            }
        }

        setProgressBarIndeterminateVisibility(true);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(EXTRA_UNIONPAY, mIsUnionPay);
        outState.putString(EXTRA_UNIONPAY_ENROLLMENT_ID, mEnrollmentId);
    }

    @Override
    protected void reset() {
        setProgressBarIndeterminateVisibility(true);
        mPayPalButton.setVisibility(GONE);
        mAndroidPayButton.setVisibility(GONE);
        mPurchaseButton.setEnabled(false);
    }

    @Override
    protected void onAuthorizationFetched() {
        try {
            mBraintreeFragment = BraintreeFragment.newInstance(this, mAuthorization);
        } catch (InvalidArgumentException e) {
            onError(e);
        }

        setProgressBarIndeterminateVisibility(false);
        mPurchaseButton.setEnabled(true);
    }

    @Override
    public void onConfigurationFetched(Configuration configuration) {
        mConfiguration = configuration;

        mCardForm.cardRequired(true)
                .expirationRequired(true)
                .cvvRequired(configuration.isCvvChallengePresent())
                .postalCodeRequired(configuration.isPostalCodeChallengePresent())
                .mobileNumberRequired(false)
                .actionLabel(getString(R.string.purchase))
                .setup(this);

        if (configuration.isPayPalEnabled()) {
            mPayPalButton.setVisibility(VISIBLE);
        }

        if (configuration.getAndroidPay().isEnabled(this)) {
            AndroidPay.isReadyToPay(mBraintreeFragment, new BraintreeResponseListener<Boolean>() {
                @Override
                public void onResponse(Boolean isReadyToPay) {
                    if (isReadyToPay) {
                        mAndroidPayButton.setVisibility(VISIBLE);
                    }
                }
            });
        }

        if (getIntent().getBooleanExtra(MainActivity.EXTRA_COLLECT_DEVICE_DATA, false)) {
            DataCollector.collectDeviceData(mBraintreeFragment, new BraintreeResponseListener<String>() {
                @Override
                public void onResponse(String deviceData) {
                    mDeviceData = deviceData;
                }
            });
        }
    }

    @Override
    public void onCardFormFieldFocused(View field) {
        if (!(field instanceof CardEditText) && !TextUtils.isEmpty(mCardForm.getCardNumber())) {
            CardType cardType = CardType.forCardNumber(mCardForm.getCardNumber());
            if (mCardType != cardType) {
                mCardType  = cardType;

                if (mConfiguration.getUnionPay().isEnabled()) {
                    UnionPay.fetchCapabilities(mBraintreeFragment, mCardForm.getCardNumber());
                }
            }
        }
    }

    @Override
    public void onCapabilitiesFetched(UnionPayCapabilities capabilities) {
        mSmsCodeContainer.setVisibility(GONE);
        mSmsCode.setText("");

        if (capabilities.isUnionPay()) {
            if (!capabilities.isSupported()) {
                mCardForm.setCardNumberError(getString(R.string.bt_card_not_accepted));
                return;
            }
            mIsUnionPay = true;
            mEnrollmentId = null;

            mCardForm.cardRequired(true)
                    .expirationRequired(true)
                    .cvvRequired(true)
                    .postalCodeRequired(mConfiguration.isPostalCodeChallengePresent())
                    .mobileNumberRequired(true)
                    .actionLabel(getString(R.string.purchase))
                    .setup(this);

            mSendSmsButton.setVisibility(VISIBLE);
        } else {
            mIsUnionPay = false;

            mCardForm.cardRequired(true)
                    .expirationRequired(true)
                    .cvvRequired(mConfiguration.isCvvChallengePresent())
                    .postalCodeRequired(mConfiguration.isPostalCodeChallengePresent())
                    .mobileNumberRequired(false)
                    .actionLabel(getString(R.string.purchase))
                    .setup(this);

            if (!mConfiguration.isCvvChallengePresent()) {
                ((EditText) findViewById(R.id.bt_card_form_cvv)).setText("");
            }
        }
    }

    public void sendSms(View v) {
        UnionPayCardBuilder unionPayCardBuilder = new UnionPayCardBuilder()
                .cardNumber(mCardForm.getCardNumber())
                .expirationMonth(mCardForm.getExpirationMonth())
                .expirationYear(mCardForm.getExpirationYear())
                .cvv(mCardForm.getCvv())
                .postalCode(mCardForm.getPostalCode())
                .mobileCountryCode(mCardForm.getCountryCode())
                .mobilePhoneNumber(mCardForm.getMobileNumber());

        UnionPay.enroll(mBraintreeFragment, unionPayCardBuilder);
    }

    @Override
    public void onSmsCodeSent(String enrollmentId, boolean smsCodeRequired) {
        mEnrollmentId = enrollmentId;
        if (smsCodeRequired) {
            mSmsCodeContainer.setVisibility(VISIBLE);
        } else {
            onCardFormSubmit();
        }
    }

    @Override
    public void onCardFormSubmit() {
        onPurchase(null);
    }

    public void launchPayPal(View v) {
        setProgressBarIndeterminateVisibility(true);

        String paymentType = Settings.getPayPalPaymentType(this);
        if (paymentType.equals(getString(R.string.paypal_billing_agreement))) {
            PayPal.requestBillingAgreement(mBraintreeFragment, new PayPalRequest());
        } else if (paymentType.equals(getString(R.string.paypal_future_payment))) {
            if (Settings.isPayPalAddressScopeRequested(this)) {
                PayPal.authorizeAccount(mBraintreeFragment, Collections.singletonList(PayPal.SCOPE_ADDRESS));
            } else {
                PayPal.authorizeAccount(mBraintreeFragment);
            }
        } else if (paymentType.equals(getString(R.string.paypal_single_payment))) {
            PayPal.requestOneTimePayment(mBraintreeFragment, new PayPalRequest("1.00"));
        }
    }

    public void launchAndroidPay(View v) {
        setProgressBarIndeterminateVisibility(true);

        ArrayList<CountrySpecification> allowedCountries = new ArrayList<>();
        for (String country : Settings.getAndroidPayAllowedCountriesForShipping(this)) {
            allowedCountries.add(new CountrySpecification(country));
        }

        AndroidPay.requestAndroidPay(mBraintreeFragment, mCart, Settings.isAndroidPayShippingAddressRequired(this),
                Settings.isAndroidPayPhoneNumberRequired(this), allowedCountries);
    }

    public void onPurchase(View v) {
        setProgressBarIndeterminateVisibility(true);

        if (mIsUnionPay) {
            UnionPayCardBuilder unionPayCardBuilder = new UnionPayCardBuilder()
                    .cardNumber(mCardForm.getCardNumber())
                    .expirationMonth(mCardForm.getExpirationMonth())
                    .expirationYear(mCardForm.getExpirationYear())
                    .cvv(mCardForm.getCvv())
                    .postalCode(mCardForm.getPostalCode())
                    .mobileCountryCode(mCardForm.getCountryCode())
                    .mobilePhoneNumber(mCardForm.getMobileNumber())
                    .smsCode(mSmsCode.getText().toString())
                    .enrollmentId(mEnrollmentId);

            UnionPay.tokenize(mBraintreeFragment, unionPayCardBuilder);
        } else {
            CardBuilder cardBuilder = new CardBuilder()
                    .cardNumber(mCardForm.getCardNumber())
                    .expirationMonth(mCardForm.getExpirationMonth())
                    .expirationYear(mCardForm.getExpirationYear())
                    .cvv(mCardForm.getCvv())
                    .postalCode(mCardForm.getPostalCode());

            Card.tokenize(mBraintreeFragment, cardBuilder);
        }
    }

    @Override
    public void onCancel(int requestCode) {
        super.onCancel(requestCode);
        setProgressBarIndeterminateVisibility(false);
    }

    @Override
    public void onPaymentMethodNonceCreated(PaymentMethodNonce paymentMethodNonce) {
        super.onPaymentMethodNonceCreated(paymentMethodNonce);

        Intent intent = new Intent()
                .putExtra(MainActivity.EXTRA_PAYMENT_METHOD_NONCE, paymentMethodNonce)
                .putExtra(MainActivity.EXTRA_DEVICE_DATA, mDeviceData);
        setResult(RESULT_OK, intent);
        finish();
    }

    @Override
    public void onError(Exception error) {
        super.onError(error);
        setProgressBarIndeterminateVisibility(false);
    }
}
