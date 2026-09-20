package com.sarthak.modelstash.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.sarthak.modelstash.MainActivity;
import com.sarthak.modelstash.R;

/** Tab 3: kits you'd like to buy. Same cards as the Catalogue, kept apart from what you own. */
public class WishlistFragment extends Fragment {

    private boolean listAnimated;

    public WishlistFragment() {
        super(R.layout.fragment_wishlist);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        MainViewModel viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        MainActivity activity = (MainActivity) requireActivity();

        TextView countText = view.findViewById(R.id.text_wishlist_count);
        View emptyState = view.findViewById(R.id.empty_state);
        RecyclerView grid = view.findViewById(R.id.list_wishlist);

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

        view.findViewById(R.id.button_empty_add).setOnClickListener(v -> activity.openAddScreen(true));

        viewModel.wishlist.observe(getViewLifecycleOwner(), models -> {
            adapter.submitList(models);
            boolean empty = models.isEmpty();
            emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
            grid.setVisibility(empty ? View.GONE : View.VISIBLE);
            countText.setVisibility(empty ? View.GONE : View.VISIBLE);
            countText.setVisibility(empty ? View.GONE : View.VISIBLE);
            countText.setText(getResources().getQuantityString(
                    R.plurals.wishlist_count, models.size(), models.size()));
            if (!listAnimated && !empty) {
                listAnimated = true;
                grid.scheduleLayoutAnimation();
            }
        });
    }
}