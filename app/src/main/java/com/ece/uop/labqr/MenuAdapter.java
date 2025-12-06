package com.ece.uop.labqr;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class MenuAdapter extends RecyclerView.Adapter<MenuAdapter.ViewHolder> {

    private final List<MenuItem> menuItems;
    private final OnItemClickListener onItemClickListener;
    private final boolean[] enabledStates;

    public MenuAdapter(List<MenuItem> menuItems, OnItemClickListener onItemClickListener) {
        this.menuItems = menuItems;
        this.onItemClickListener = onItemClickListener;
        this.enabledStates = new boolean[menuItems.size()];

        for (int i = 0; i < enabledStates.length; i++) {
            enabledStates[i] = true;
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.menu_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MenuItem menuItem = menuItems.get(position);
        holder.icon.setImageResource(menuItem.getIconResId());
        holder.text.setText(menuItem.getText());

        holder.itemView.setAlpha(enabledStates[position] ? 1.0f : 0.5f);
        holder.itemView.setEnabled(enabledStates[position]);
        holder.itemView.setOnClickListener(v -> {
            if (onItemClickListener != null && enabledStates[position]) {
                onItemClickListener.onItemClick(position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return menuItems.size();
    }

    public void setEnabled(int position, boolean enabled) {
        enabledStates[position] = enabled;
        notifyItemChanged(position);
    }

    public void setAllEnabled(boolean enabled) {
        for (int i = 0; i < enabledStates.length; i++) {
            enabledStates[i] = enabled;
        }
        notifyDataSetChanged();
    }

    public interface OnItemClickListener {
        void onItemClick(int position);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView text;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.menu_icon);
            text = itemView.findViewById(R.id.menu_text);
        }
    }
}
