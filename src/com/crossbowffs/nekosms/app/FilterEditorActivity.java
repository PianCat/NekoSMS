package com.crossbowffs.nekosms.app;

import android.app.Fragment;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;
import com.crossbowffs.nekosms.R;
import com.crossbowffs.nekosms.data.SmsFilterData;
import com.crossbowffs.nekosms.data.SmsFilterField;
import com.crossbowffs.nekosms.data.SmsFilterMode;
import com.crossbowffs.nekosms.data.SmsFilterPatternData;
import com.crossbowffs.nekosms.loader.FilterRuleLoader;
import com.crossbowffs.nekosms.widget.FragmentPagerAdapter;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class FilterEditorActivity extends AppCompatActivity {
    private class FilterEditorPageAdapter extends FragmentPagerAdapter {
        public FilterEditorPageAdapter() {
            super(getFragmentManager());
        }

        @Override
        public Fragment getItem(int position) {
            FilterEditorFragment fragment = new FilterEditorFragment();
            Bundle args = new Bundle(1);
            int mode;
            if (position == 0) {
                mode = FilterEditorFragment.EXTRA_MODE_SENDER;
            } else if (position == 1) {
                mode = FilterEditorFragment.EXTRA_MODE_BODY;
            } else {
                throw new AssertionError("Invalid adapter position: " + position);
            }
            args.putInt(FilterEditorFragment.EXTRA_MODE, mode);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public int getCount() {
            return 2;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            if (position == 0) {
                return getString(R.string.filter_field_sender);
            } else if (position == 1) {
                return getString(R.string.filter_field_body);
            } else {
                throw new AssertionError("Invalid adapter position: " + position);
            }
        }
    }

    private Toolbar mToolbar;
    private TabLayout mTabLayout;
    private ViewPager mViewPager;
    private Uri mFilterUri;
    private SmsFilterData mFilter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_filter_editor);
        mToolbar = (Toolbar)findViewById(R.id.toolbar);
        mTabLayout = (TabLayout)findViewById(R.id.filter_editor_tablayout);
        mViewPager = (ViewPager)findViewById(R.id.filter_editor_viewpager);

        // Set up toolbar
        mToolbar.setTitle(R.string.save_filter);
        mToolbar.setNavigationIcon(R.drawable.ic_done_white_24dp);
        setSupportActionBar(mToolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Set up tab pages
        mViewPager.setAdapter(new FilterEditorPageAdapter());
        mTabLayout.setupWithViewPager(mViewPager);

        // Process intent for modifying existing filter if it exists
        mFilterUri = getIntent().getData();
        if (mFilterUri != null) {
            mFilter = FilterRuleLoader.get().query(this, mFilterUri);
        } else {
            mFilter = new SmsFilterData();
        }

        // Select a tab based on which pattern has data
        // Default to the sender tab if neither has data
        if (!mFilter.getSenderPattern().hasData() && mFilter.getBodyPattern().hasData()) {
            mTabLayout.getTabAt(1).select();
        } else {
            mTabLayout.getTabAt(0).select();
        }

        // Initialize empty patterns with some reasonable default values
        if (!mFilter.getSenderPattern().hasData()) {
            mFilter.getSenderPattern()
                .setPattern("")
                .setMode(SmsFilterMode.CONTAINS)
                .setCaseSensitive(false);
        }

        if (!mFilter.getBodyPattern().hasData()) {
            mFilter.getBodyPattern()
                .setPattern("")
                .setMode(SmsFilterMode.CONTAINS)
                .setCaseSensitive(false);
        }
    }

    @Override
    public void onBackPressed() {
        saveIfValid();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.options_filter_editor, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            saveIfValid();
            return true;
        case R.id.menu_item_discard_changes:
            discardAndFinish();
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    public SmsFilterPatternData getPatternData(SmsFilterField field) {
        switch (field) {
        case SENDER:
            return mFilter.getSenderPattern();
        case BODY:
            return mFilter.getBodyPattern();
        default:
            throw new AssertionError("Invalid filter field: " + field);
        }
    }

    private String validatePatternString(SmsFilterPatternData patternData, int fieldNameId) {
        if (patternData.getMode() != SmsFilterMode.REGEX) {
            return null;
        }
        String pattern = patternData.getPattern();
        try {
            // We don't need the actual compiled pattern, this
            // is just to make sure the syntax is valid
            Pattern.compile(pattern);
        } catch (PatternSyntaxException e) {
            String description = e.getDescription();
            if (description == null) {
                description = getString(R.string.invalid_pattern_reason_unknown);
            }
            return getString(R.string.format_invalid_pattern_message, getString(fieldNameId), description);
        }
        return null;
    }

    private boolean shouldSaveFilter() {
        return mFilter.getSenderPattern().hasData() || mFilter.getBodyPattern().hasData();
    }

    private boolean validatePattern(SmsFilterPatternData patternData, int fieldNameId, int tabIndex) {
        if (!patternData.hasData()) {
            return true;
        }
        String patternError = validatePatternString(patternData, fieldNameId);
        if (patternError == null) {
            return true;
        }
        mTabLayout.getTabAt(tabIndex).select();
        showInvalidPatternDialog(patternError);
        return false;
    }

    private void saveIfValid() {
        if (!shouldSaveFilter()) {
            discardAndFinish();
            return;
        }
        if (!validatePattern(mFilter.getSenderPattern(), R.string.invalid_pattern_field_sender, 0)) {
            return;
        }
        if (!validatePattern(mFilter.getBodyPattern(), R.string.invalid_pattern_field_body, 1)) {
            return;
        }
        saveAndFinish();
    }

    private void saveAndFinish() {
        Uri filterUri = persistFilterData();
        int messageId = (filterUri != null) ? R.string.filter_saved : R.string.filter_save_failed;
        Toast.makeText(this, messageId, Toast.LENGTH_SHORT).show();
        Intent intent = new Intent();
        intent.setData(filterUri);
        setResult(RESULT_OK, intent);
        ActivityCompat.finishAfterTransition(this);
    }

    private void discardAndFinish() {
        setResult(RESULT_CANCELED, null);
        ActivityCompat.finishAfterTransition(this);
    }

    private Uri persistFilterData() {
        return FilterRuleLoader.get().update(this, mFilterUri, mFilter, true);
    }

    private void showInvalidPatternDialog(String errorMessage) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
            .setTitle(R.string.invalid_pattern_title)
            .setMessage(errorMessage)
            .setIcon(R.drawable.ic_warning_white_24dp)
            .setPositiveButton(R.string.ok, null);

        AlertDialog dialog = builder.create();
        dialog.show();
    }
}
