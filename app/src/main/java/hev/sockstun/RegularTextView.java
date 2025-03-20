package hev.sockstun;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;

public class RegularTextView extends androidx.appcompat.widget.AppCompatTextView {
    public RegularTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void setTypeface(Typeface tf) {
        tf = Typeface.createFromAsset(getContext().getAssets(), "fonts/NotoSansSC-Regular.ttf");
        super.setTypeface(tf);
    }
}
