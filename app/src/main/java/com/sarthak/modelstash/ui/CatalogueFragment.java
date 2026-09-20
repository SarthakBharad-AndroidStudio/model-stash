package com.sarthak.modelstash.ui;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.textfield.TextInputEditText;
import com.sarthak.modelstash.MainActivity;
import com.sarthak.modelstash.R;
import com.sarthak.modelstash.data.ModelKit;
import com.sarthak.modelstash.util.Haptics;
import com.sarthak.modelstash.util.ModelSort;

import java.util.List;

/** Tab 2: every model as a photo grid, with search, sorting and a clear "do I own it?" answer. */
public class CatalogueFragment extends Fragment {

    private MainViewModel viewModel;
    private MaterialCardView statusCard;
    private ImageView statusIcon;
    private TextView statusText;
    private MaterialButton addButton;
    private boolean listAnimated;

    public CatalogueFragment() {
        super(R.layout.fragment_catalogue);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        MainActivity activity = (MainActivity) requireActivity();

        TextInputEditText searchInput = view.findViewById(R.id.input_search);
        ChipGroup sortChips = view.findViewById(R.id.chips_sort);
        statusCard = view.findViewById(R.id.card_status);
        statusIcon = view.findViewById(R.id.icon_status);
        statusText = view.findViewById(R.id.text_status);
        addButton = view.findViewById(R.id.button_add_searched);
        RecyclerView grid = view.findViewById(R.id.list_models);

        ModelAdapter adapter = new ModelAdapter(R.layout.item_model_grid,
                (model, photo) -> ModelDetailActivity.start(requireActivity(), model.id, photo));
        grid.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        grid.setAdapter(adapter);
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                activity.onContentScrolled(dy);
            }
        });

        // Search
        if (savedInstanceState == null) {
            searchInput.setText(viewModel.getQuery()); // keep the search when coming back to this tab
        }
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                viewModel.setQuery(s.toString());
            }
        });

        // Sort chips
        sortChips.check(chipFor(viewModel.getSortMode()));
        sortChips.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                return;
            }
            Haptics.tick(group);
            viewModel.setSortMode(modeFor(checkedIds.get(0)));
            grid.scrollToPosition(0);
        });

        addButton.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), AddEditModelActivity.class);
            intent.putExtra(AddEditModelActivity.EXTRA_PREFILL_NAME, viewModel.getQuery().trim());
            startActivity(intent);
        });

        viewModel.visibleModels.observe(getViewLifecycleOwner(), models -> {
            adapter.submitList(models);
            showOwnership(models);
            if (!listAnimated && !models.isEmpty()) {
                listAnimated = true;
                grid.scheduleLayoutAnimation(); // cards rise in one after another, once
            }
        });
    }

    private static int chipFor(ModelSort.Mode mode) {
        switch (mode) {
            case NEWEST:
                return R.id.chip_sort_newest;
            case SCALE:
                return R.id.chip_sort_scale;
            case NAME:
            default:
                return R.id.chip_sort_name;
        }
    }

    private static ModelSort.Mode modeFor(int chipId) {
        if (chipId == R.id.chip_sort_newest) {
            return ModelSort.Mode.NEWEST;
        }
        if (chipId == R.id.chip_sort_scale) {
            return ModelSort.Mode.SCALE;
        }
        return ModelSort.Mode.NAME;
    }

    /** Colours and words the status card: neutral total, green "you own it", or red "you don't". */
    private void showOwnership(List<ModelKit> results) {
        String query = viewModel.getQuery().trim();

        if (query.isEmpty()) {
            statusText.setText(getResources().getQuantityString(
                    R.plurals.catalogue_total, results.size(), results.size()));
            paintStatus(MaterialColors.getColor(statusCard,
                            com.google.android.material.R.attr.colorSurfaceContainer),
                    MaterialColors.getColor(statusCard,
                            com.google.android.material.R.attr.colorOnSurfaceVariant));
            statusIcon.setVisibility(View.GONE);
            addButton.setVisibility(View.GONE);
            return;
        }

        boolean exactMatch = false;
        for (ModelKit m : results) {
            if (m.name.equalsIgnoreCase(query)) {
                exactMatch = true;
                break;
            }
        }

        if (results.isEmpty()) {
            statusText.setText(getString(R.string.status_not_owned, query));
            statusIcon.setImageResource(R.drawable.ic_close);
            paintStatus(MaterialColors.getColor(statusCard,
                            com.google.android.material.R.attr.colorErrorContainer),
                    MaterialColors.getColor(statusCard,
                            com.google.android.material.R.attr.colorOnErrorContainer));
        } else {
            statusText.setText(exactMatch
                    ? getString(R.string.status_owned, query)
                    : getResources().getQuantityString(
                    R.plurals.status_partial_matches, results.size(), results.size(), query));
            statusIcon.setImageResource(R.drawable.ic_check);
            paintStatus(ContextCompat.getColor(requireContext(), R.color.c_owned_container),
                    ContextCompat.getColor(requireContext(), R.color.c_owned));
        }
        statusIcon.setVisibility(View.VISIBLE);

        // Offer to add it unless you already own something with exactly this name.
        addButton.setVisibility(exactMatch ? View.GONE : View.VISIBLE);
    }

    private void paintStatus(int background, int foreground) {
        statusCard.setCardBackgroundColor(background);
        statusText.setTextColor(foreground);
        statusIcon.setImageTintList(ColorStateList.valueOf(foreground));
        addButton.setTextColor(foreground);
        addButton.setIconTint(ColorStateList.valueOf(foreground));
    }
}