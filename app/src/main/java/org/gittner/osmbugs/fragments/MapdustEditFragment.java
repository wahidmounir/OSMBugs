package org.gittner.osmbugs.fragments;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.support.v4.app.Fragment;
import android.support.v4.widget.ContentLoadingProgressBar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Background;
import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EFragment;
import org.androidannotations.annotations.FragmentArg;
import org.androidannotations.annotations.OptionsItem;
import org.androidannotations.annotations.OptionsMenu;
import org.androidannotations.annotations.OptionsMenuItem;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;
import org.gittner.osmbugs.R;
import org.gittner.osmbugs.api.Apis;
import org.gittner.osmbugs.api.MapdustApi;
import org.gittner.osmbugs.bugs.MapdustBug;
import org.gittner.osmbugs.common.Comment;
import org.gittner.osmbugs.statics.Settings;

import java.util.List;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

@EFragment(R.layout.fragment_mapdust_edit)
@OptionsMenu(R.menu.mapdust_edit)
public class MapdustEditFragment extends Fragment
{
    public static final String ARG_BUG = "ARG_BUG";

    @FragmentArg(ARG_BUG)
    MapdustBug mBug;

    @ViewById(R.id.creationDate)
    TextView mCreationDate;
    @ViewById(R.id.txtvDescription)
    TextView mDescription;
    @ViewById(R.id.pbarLoadingComments)
    ContentLoadingProgressBar mProgressBarComments;
    @ViewById(R.id.lstvComments)
    ListView mComments;
    @ViewById(R.id.imgbtnAddComment)
    ImageButton mAddComment;

    @OptionsMenuItem(R.id.action_close)
    MenuItem mMenuCloseBug;
    @OptionsMenuItem(R.id.action_ignore)
    MenuItem mMenuIgnoreBug;

    private CommentAdapter mAdapter;

    private ProgressDialog mSaveDialog = null;


    @AfterViews
    void init()
    {
        mCreationDate.setText(mBug.getCreationDate().toString(getString(R.string.date_time_format)));

        mDescription.setText(mBug.getDescription());

        mAdapter = new CommentAdapter(getActivity());
        mComments.setAdapter(mAdapter);

        if (mBug.getState() == MapdustBug.STATE.CLOSED)
        {
            mAddComment.setVisibility(GONE);
        }

        mSaveDialog = new ProgressDialog(getActivity());
        mSaveDialog.setTitle(R.string.saving);
        mSaveDialog.setMessage(getString(R.string.please_wait));
        mSaveDialog.setCancelable(false);
        mSaveDialog.setIndeterminate(true);

        mProgressBarComments.show();

        loadComments();
    }


    @Background
    void loadComments()
    {
        List<Comment> comments = Apis.MAPDUST.retrieveComments(mBug.getId());

        commentsLoaded(comments);
    }


    @UiThread
    void commentsLoaded(List<Comment> comments)
    {
        mBug.setComments(comments);

        mAdapter.addAll(comments);
        mAdapter.notifyDataSetChanged();

        mProgressBarComments.setVisibility(View.GONE);
    }


    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
    {
        boolean visible = mBug.getState() == MapdustBug.STATE.OPEN;

        mMenuCloseBug.setVisible(visible);
        mMenuIgnoreBug.setVisible(visible);
    }


    @OptionsItem(R.id.action_close)
    void menuCloseBugClicked()
    {
        final EditText resolveComment = new EditText(getActivity());
        new AlertDialog.Builder(getActivity())
                .setView(resolveComment)
                .setCancelable(true)
                .setTitle(R.string.enter_comment)
                .setPositiveButton(R.string.close, (dialogInterface, i) -> {
                    mSaveDialog.show();

                    uploadBugStatus(
                            MapdustBug.STATE.CLOSED,
                            resolveComment.getText().toString());
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }


    @OptionsItem(R.id.action_ignore)
    void menuIgnoreBugClicked()
    {
        final EditText resolveComment = new EditText(getActivity());
        new AlertDialog.Builder(getActivity())
                .setView(resolveComment)
                .setCancelable(true)
                .setTitle(R.string.enter_comment)
                .setPositiveButton(R.string.close, (dialogInterface, i) ->  {
                    mSaveDialog.show();

                    uploadBugStatus(
                            MapdustBug.STATE.IGNORED,
                            resolveComment.getText().toString());
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }


    @Background
    void uploadBugStatus(MapdustBug.STATE state, String message)
    {
        boolean result = Apis.MAPDUST.changeBugStatus(
                mBug.getId(),
                state,
                message,
                Settings.Mapdust.getUsername());

        uploadDone(result);
    }


    @UiThread
    void uploadDone(boolean result)
    {
        mSaveDialog.dismiss();

        if (result)
        {
            getActivity().setResult(Activity.RESULT_OK);
            getActivity().finish();
        }
        else
        {
            new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.error)
                    .setMessage(R.string.failed_to_save_bug)
                    .setCancelable(true)
                    .show();
        }
    }


    @Click(R.id.imgbtnAddComment)
    void addComment()
    {
        final EditText newComment = new EditText(getActivity());
        new AlertDialog.Builder(getActivity())
                .setView(newComment)
                .setCancelable(true)
                .setMessage(R.string.enter_comment)
                .setPositiveButton(R.string.ok, (dialogInterface, i) -> {
                    mSaveDialog.show();

                    uploadComment(newComment.getText().toString());
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }


    @Background
    void uploadComment(String comment)
    {
        boolean result = new MapdustApi().commentBug(
                mBug.getId(),
                comment,
                Settings.Mapdust.getUsername());

        uploadDone(result);
    }


    public class CommentAdapter extends ArrayAdapter<Comment>
    {
        public CommentAdapter(Context context)
        {
            super(context, R.layout.row_comment);
        }


        @Override
        public View getView(int position, View convertView, ViewGroup parent)
        {
            View v = convertView != null ? convertView : LayoutInflater.from(getContext()).inflate(R.layout.row_comment, parent, false);

            Comment comment = getItem(position);

            TextView username = v.findViewById(R.id.username);
            if (!comment.getUsername().equals(""))
            {
                username.setVisibility(VISIBLE);
                username.setText(comment.getUsername());
            }
            else
            {
                username.setVisibility(GONE);
            }

            TextView text = v.findViewById(R.id.text);
            text.setText(comment.getText());

            return v;
        }
    }
}
