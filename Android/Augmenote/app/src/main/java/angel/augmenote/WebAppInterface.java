package angel.augmenote;
import android.content.Context;
import android.content.Intent;
import android.webkit.JavascriptInterface;

public class WebAppInterface {
        Context mContext;

        /** Instantiate the interface and set the context */
        WebAppInterface(Context c) {
            mContext = c;
        }


        /** Show a toast from the web page */
        @JavascriptInterface
        public void loadAugment() {
            Intent mainIntent = new Intent(mContext, MainActivity.class);
            mContext.startActivity(mainIntent);
        }


        private void startActivity(Intent mainIntent) {
            // TODO Auto-generated method stub

        }
}
