package com.mycelium.wallet.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageView;
import com.mycelium.wallet.R;

import java.lang.reflect.Field;

//TODO: upgrade to android support v7 >>19.1.0
@SuppressLint("AppCompatCustomView")
public class TransactionConfirmationsDisplay extends ImageView{
   public static final int MAX_CONFIRMATIONS = 6;

   public TransactionConfirmationsDisplay(Context context) {
      super(context);
      setConfirmations(0);
   }

   public TransactionConfirmationsDisplay(Context context, AttributeSet attrs) {
      super(context, attrs);
      setConfirmations(0);
   }

   public TransactionConfirmationsDisplay(Context context, AttributeSet attrs, int defStyle) {
      super(context, attrs, defStyle);
      setConfirmations(0);
   }

   public void setNeedsBroadcast(){
      try {
         Class res = R.drawable.class;
         Field field = res.getField("pie_send");
         int drawableId = field.getInt(null);
         setImageResource(drawableId);
      } catch (NoSuchFieldException e) {
         throw new RuntimeException("drawable not found, pie_send");
      } catch (IllegalAccessException e) {
         throw new RuntimeException("drawable not found, pie_send");
      }

   }

   public void setConfirmations(int number){

      if (number > MAX_CONFIRMATIONS){
         number = MAX_CONFIRMATIONS;
      }else if (number < 0){
         number = 0;
      }

      try {
         Class res = R.drawable.class;
         Field field = res.getField("pie_" + number);
         int drawableId = field.getInt(null);
         setImageResource(drawableId);
      } catch (NoSuchFieldException e) {
         throw new RuntimeException("drawable not found, " + number);
      } catch (IllegalAccessException e) {
         throw new RuntimeException("drawable not found, " + number);
      }

   }
}

/*
generate pie graphics:

http://jsfiddle.net/a02gw5xf/2/

convert all.png -fuzz 2% -trim +repage trim1.png  #white
convert all.png -fuzz 2% -trim +repage trim.png   #black

for i in {0..6}; do; echo $i; convert trim.png -repage 0x0 -crop "0x22+0+$(($i*24))" -fuzz 4% -transparent black  pie_$i.png; done;

 */
