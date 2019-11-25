package nourl.tbd.Blipp.Database;

import android.app.Activity;
import android.os.AsyncTask;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import nourl.tbd.Blipp.BlippConstructs.Blipp;

public class BlipSender extends AsyncTask<Void, Boolean, Void>
{

    FirebaseDatabase db;
    DatabaseReference location;
    Blipp blip;
    BlipSenderCompletion completion;


   public BlipSender(Blipp blip, BlipSenderCompletion completion)
    {
        this.blip = blip;
        this.completion = completion;
        db = FirebaseDatabase.getInstance("https://blipp-15ee8.firebaseio.com/");
        location = db.getReference().child("blip");
        this.execute();
    }


    @Override
    protected Void doInBackground(Void... voids)
    {
        DatabaseReference here = location.push();
        here.setValue(blip.withId(here.getKey())).addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task)
            {
            onProgressUpdate(task.isSuccessful());
            }
        });
        return null;
    }


    @Override
    protected void onProgressUpdate(Boolean... values) {
        completion.blipSenderDone(values[0]);
    }
}
