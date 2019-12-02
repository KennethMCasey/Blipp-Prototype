package nourl.tbd.Blipp.UI;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.UploadTask;

import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;

import nourl.tbd.Blipp.BlippConstructs.Blipp;
import nourl.tbd.Blipp.BlippConstructs.Community;
import nourl.tbd.Blipp.Database.BlipGetter;
import nourl.tbd.Blipp.Database.BlipGetterCompletion;
import nourl.tbd.Blipp.Database.BlipSender;
import nourl.tbd.Blipp.Database.BlipSenderCompletion;
import nourl.tbd.Blipp.Helper.BlipListAdapter;
import nourl.tbd.Blipp.Helper.StatePersistence;
import nourl.tbd.Blipp.R;

import static android.app.Activity.RESULT_OK;

public class CommunityBlipsFragment extends Fragment implements BlipGetterCompletion, BlipSenderCompletion {
    static final int REQUEST_IMAGE_GET = 1;


    //class properties
    private Community community;
    private Spinner blippOrder;
    private ListView blippFeed;
    private SwipeRefreshLayout blippRefresh;
    private FloatingActionButton fab;
    boolean didHitBottom;
    FragmentSwap fragmentSwap;

    View popupView;
    PopupWindow popupWindow;
    boolean popUpIsShowing;
    String currentPhotoPath;
    URL currentPhotoUrl;


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //                                      ACTIVITY LIFE CYCLE                                                   //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState)
    {

        fragmentSwap = (FragmentSwap) this.getActivity();



        Bundle b = getArguments();
        String comID = b.getString("id", "");
        String name =b.getString("name", "");
        String owner =b.getString("owner", "");
        Double lat = b.getDouble("lat", -1);
        Double lon = b.getDouble("lon", -1);
        Double radius = b.getDouble("radius", -1);
        String photo = b.getString("photo", null );
        boolean join = b.getBoolean("joinable", false);

        try {
            community = new Community(comID, photo == null ? null : new URL(photo), lat, lon, radius, name, join, owner);
        }
        catch (Exception e) {}

        //inflates the layout
        //TODO: Make the layout look nicer
        View v = inflater.inflate(R.layout.comunity_blip_feed, container, false);

        //Configures the floating action button
        fab = v.findViewById(R.id.fab_blipp);
        fab.setOnClickListener(new MakeBlipp());

        //Configures the swipe refresh
        blippRefresh = v.findViewById(R.id.swiperefresh_feed);
        blippRefresh.setOnRefreshListener(new RefreshFeed());


        //set the name of the community
        ((TextView)v.findViewById(R.id.community_name)).setText("Selected Community: " + community.getName());


        //Configures the blipp order selector
        blippOrder = v.findViewById(R.id.spinner_order);
        ArrayAdapter<CharSequence> adapter2 = ArrayAdapter.createFromResource(this.getContext(), R.array.blipp_order, R.layout.spinner_item_blip);
        adapter2.setDropDownViewResource(R.layout.spinner_item_blip);
        blippOrder.setAdapter(adapter2);
        blippOrder.setSelection(StatePersistence.current.nearMeSelectedOrdering,false);
        blippOrder.setOnItemSelectedListener(new BlippSpinnerChanged());

        //Configures the blipp feed list
        blippFeed = v.findViewById(R.id.list_feed);
        blippFeed.setAdapter(new BlipListAdapter(this.getContext(), StatePersistence.current.blipsFeed == null ? new ArrayList<Blipp>() : StatePersistence.current.blipsFeed));
        blippFeed.setOnScrollListener(new BottomHit());
        blippFeed.setOnItemClickListener(new ToBlipDetail());

        //if there were no previously loaded blipps this will start the background task to get the new blipps
        getBlips(null);


        //set the touch listener for if the user clicks outside the make blip window
        v.setOnTouchListener(new AnythingTouched());
        blippOrder.setOnTouchListener(new AnythingTouched());



        return v;
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
        if (popUpIsShowing)
        {
            //remove the popupWindow if the fragment is destroyed
            popupWindow.dismiss();
            popUpIsShowing = false;
        }
    }


    @Override
    public void onResume()
    {
        //TODO: Fix me
        super.onResume();
        if (StatePersistence.current.blipsFeed != null && StatePersistence.current.blipsFeed.size() != blippFeed.getAdapter().getCount()) ((BlipListAdapter)blippFeed.getAdapter()).notifyDataSetChanged();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_IMAGE_GET && resultCode == RESULT_OK) {
            Bitmap thumbnail = data.getParcelableExtra("data");
            Uri fullPhotoUri = data.getData();
            ImageView iv = popupView.findViewById(R.id.make_blipp_photo);
            iv.setImageURI(fullPhotoUri);
            currentPhotoPath = FirebaseStorage.getInstance().getReference().child(UUID.randomUUID().toString()).getPath();
            FirebaseStorage.getInstance().getReference(currentPhotoPath).putFile(fullPhotoUri).addOnCompleteListener(new OnCompleteListener<UploadTask.TaskSnapshot>() {
                @Override
                public void onComplete(@NonNull Task<UploadTask.TaskSnapshot> task)
                {
                    Toast.makeText(getContext(), task.isSuccessful() ? "Good" : "Bad", Toast.LENGTH_LONG).show();
                    if (task.isSuccessful()) FirebaseStorage.getInstance().getReference(currentPhotoPath).getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                        @Override
                        public void onSuccess(Uri uri) {
                            try { currentPhotoUrl = new URL(uri.toString());} catch (Exception e) {}
                        }
                    });
                }
            });

        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //                                      BLIP GETTER                                                           //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////


    //This function gets the blips
    private void getBlips(String blipIdToStartAt)
    {
        didHitBottom = false;
        blippRefresh.setEnabled(true);
        blippRefresh.setRefreshing(true);

        //Order By Most Recent
        if (blippOrder.getSelectedItemPosition() == 0) new BlipGetter(BlipGetter.Order.MOST_RECENT, this, community.getId(), blipIdToStartAt, 20, this.getContext());


        //Order By Most Liked
        else if (blippOrder.getSelectedItemPosition() == 1) new BlipGetter(BlipGetter.Order.MOST_LIKED, this, community.getId(), blipIdToStartAt, 20, this.getContext());


    }

    @Override
    public void blipGetterGotInitialBlips(ArrayList<Blipp> results)
    {
        if (results == null) didHitBottom = true;
        StatePersistence.current.blipsFeed = results;
        ((BlipListAdapter)blippFeed.getAdapter()).setBlipps(results);
        blippRefresh.setRefreshing(false);
    }

    @Override
    public void blipGetterGotAdditionalBlips(ArrayList<Blipp> results)
    {
        if (results == null) didHitBottom = true;
        StatePersistence.current.blipsFeed.addAll(results);
        ((BlipListAdapter)blippFeed.getAdapter()).addBlips(results);
        blippRefresh.setRefreshing(false);
    }

    @Override
    public void blipGetterDidFail()
    {
        Toast.makeText(this.getContext(), "Error getting blips, please try again later...", Toast.LENGTH_LONG).show();
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //                                      BLIP SENDER                                                           //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public void sendBlip(String text)
    {

        try{
            Blipp temp = new Blipp(false, false, false, text, new URL(currentPhotoPath), null, community.getId(), this.getContext());
            new BlipSender(temp, this, this.getContext());
        }catch (Exception e){Toast.makeText(this.getContext(), "Exception was thrown", Toast.LENGTH_LONG).show();}

    }


    @Override
    public void blipSenderDone(boolean isSucessful)
    {
        if (isSucessful)
        {
            Toast.makeText(CommunityBlipsFragment.this.getContext(), "Blipp was sent sucessfully", Toast.LENGTH_SHORT).show();
            popupWindow.dismiss();
            popUpIsShowing = false;
        }

        else
        {
            Toast.makeText(CommunityBlipsFragment.this.getContext(), "Error Sending Blip", Toast.LENGTH_SHORT).show();
        }
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //                                      VIEW ACTION LISTENERS                                                 //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private class RefreshFeed implements SwipeRefreshLayout.OnRefreshListener
    {
        @Override
        public void onRefresh()
        {
            getBlips(null);
        }
    }

    class BlippSpinnerChanged implements Spinner.OnItemSelectedListener
    {
        @Override
        public void onItemSelected(AdapterView<?> adapterView, View view, int position, long l)
        {
            blippRefresh.setRefreshing(true);
            getBlips(null);
        }

        @Override
        public void onNothingSelected(AdapterView<?> adapterView)
        {
            //UnUsed Method
        }
    }

    private class BottomHit implements AbsListView.OnScrollListener
    {
        @Override
        public void onScrollStateChanged(AbsListView absListView, int i)
        {
            //UnUsed Method
        }

        @Override
        public void onScroll(AbsListView absListView, int firstVisableItem, int visableItemCount, int totalItemCount)
        {
            if (firstVisableItem + visableItemCount == totalItemCount && totalItemCount!=0) getBlips(((Blipp)((BlipListAdapter)blippFeed.getAdapter()).getItem(totalItemCount-1)).getId());
        }
    }

    class ToBlipDetail implements ListView.OnItemClickListener {

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id)
        {


            Blipp blipp = (Blipp)blippFeed.getAdapter().getItem(position);

            String time = new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss", Locale.ENGLISH).format(blipp.getTime());

            Bundle b = new Bundle();
            b.putString("blipID", blipp.getId());
            b.putString("blipParent", blipp.getParent());
            b.putString("blipText", blipp.getText());
            b.putString("blipUser", blipp.getUserId());
            b.putString("blipCommunity", blipp.getCommunity());
            b.putString("blipURL", blipp.getUrl() == null ? null : blipp.getUrl().toString());
            b.putDouble("blipLat", blipp.getLatitude());
            b.putDouble("blipLon", blipp.getLongitude());
            b.putString("blipTime", blipp.getTime() == null ? null : time);
            b.putBoolean("blipShort", blipp.isShortDistance());
            b.putBoolean("blipMed", blipp.isMediumDistance());
            b.putBoolean("blipLong", blipp.isLongDistance());
            BlippDetailFragment frag = new BlippDetailFragment();
            frag.setArguments(b);
            fragmentSwap.swap(frag, true);
        }
    }

    //Floating Action Button Related Classes

    class MakeBlipp implements FloatingActionButton.OnClickListener
    {
        @Override
        public void onClick(View view)
        {
            //TODO: Create new blip interface and functionality
            //Note: Must include option for blipp to be in either close, regular or max distance.
            //Note: Must include way of adding a photo to your blipp. Photos are optional but text is mandatory.
            //Note: Blipps are anonymous when posting, but please ensure to link the blipp with the current user when storing in the database so we can query them later such as in the My blipps section
            //Note: Please try and make the interface look as aesthetically pleasing as possible AFTER we get the core functionality working, try to make a clean interface we will go back and polish later.
            //Note: Look at the Fire base data face and see what other additional information is stored in each blip (ex: time, location), Some of this information isnt entered by the user but instead requires us to pull it from code.

            if (!popUpIsShowing)
            {
                LayoutInflater layoutInflater = getLayoutInflater();
                popupView = layoutInflater.inflate(R.layout.make_blipp,null);
                popupView.findViewById(R.id.check_close).setVisibility(View.GONE);
                popupView.findViewById(R.id.check_reg).setVisibility(View.GONE);
                popupView.findViewById(R.id.check_max).setVisibility(View.GONE);


                popupWindow = new PopupWindow(popupView, RelativeLayout.LayoutParams.WRAP_CONTENT,RelativeLayout.LayoutParams.WRAP_CONTENT);
                popupWindow.setFocusable(true);


                Button btnClose = (Button)popupView.findViewById(R.id.btnClose);

                btnClose.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        popupWindow.dismiss();
                        popUpIsShowing = false;
                    }
                });

                Button btnPhoto = popupView.findViewById(R.id.make_blipp_btn_image);
                btnPhoto.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {

                        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                        intent.setType("image/*");
                        if (intent.resolveActivity(getContext().getPackageManager()) != null) {
                            startActivityForResult(intent, REQUEST_IMAGE_GET);
                        }
                    }
                });

                Button btnSubmit = popupView.findViewById(R.id.btnSubmit);
                btnSubmit.setOnClickListener( new View.OnClickListener()
                {
                    @Override
                    public void onClick(View view)
                    {
                        String blipText = ((EditText)popupView.findViewById(R.id.make_blipp_text)).getText().toString();
                        sendBlip(blipText);

                        popupWindow.dismiss();
                        popUpIsShowing = false;

                    }
                });

                popupWindow.showAtLocation(CommunityBlipsFragment.this.getView().getRootView(), Gravity.CENTER, 0, 0);

                popUpIsShowing = true;
            }
        }
    }

    class AnythingTouched implements View.OnTouchListener
    {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent)
        {
            if (popUpIsShowing)
            {
                popupWindow.dismiss();
                popUpIsShowing = false;
            }
            return false;
        }
    }

}