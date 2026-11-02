package com.sarthak.modelstash.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.sarthak.modelstash.R;
import com.sarthak.modelstash.data.ModelRepository;
import com.sarthak.modelstash.util.BrandGroups;
import com.sarthak.modelstash.util.InsetsHelper;

import java.util.List;

/** Every brand in your stash, each one opening to show the kits under it. */
public class BrandsActivity extends AppCompatActivity {

    public static void start(Context context) {
        context.startActivity(new Intent(context, BrandsActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_brands);
        InsetsHelper.applySystemBars(findViewById(R.id.root), true, true);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        View emptyState = findViewById(R.id.text_empty);
        RecyclerView list = findViewById(R.id.list_brands);
        BrandAdapter adapter = new BrandAdapter(
                (model, photo) -> ModelDetailActivity.start(this, model.id, photo));
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        // Observing rather than loading once, so editing a kit's brand is reflected
        // the moment you come back to this screen. Every brand starts collapsed:
        // the point of the screen is the list of brands, not a wall of kits.
        ModelRepository.get(this).getAll().observe(this, models -> {
            List<BrandGroups.Group> groups = BrandGroups.from(models);
            adapter.submit(groups);
            emptyState.setVisibility(groups.isEmpty() ? View.VISIBLE : View.GONE);
            list.setVisibility(groups.isEmpty() ? View.GONE : View.VISIBLE);
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
