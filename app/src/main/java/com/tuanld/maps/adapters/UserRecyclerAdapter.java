package com.tuanld.maps.adapters;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;


import com.tuanld.maps.R;
import com.tuanld.maps.models.User;

import java.util.ArrayList;

public class UserRecyclerAdapter extends RecyclerView.Adapter<UserRecyclerAdapter.ViewHolder> {

    private ArrayList<User> mUsers = new ArrayList<>();
    private UserListRecylerClick mClickListener;


    public UserRecyclerAdapter(ArrayList<User> users, UserListRecylerClick clickListener) {
        this.mUsers = users;
        mClickListener = clickListener;
    }


    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.layout_user_list_item, parent, false);
        final ViewHolder holder = new ViewHolder(view, mClickListener);
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {

        ((ViewHolder) holder).username.setText(mUsers.get(position).getUsername());
        ((ViewHolder) holder).email.setText(mUsers.get(position).getEmail());
    }

    @Override
    public int getItemCount() {
        return mUsers.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        TextView username, email;
        UserListRecylerClick mClickListener;

        public ViewHolder(View itemView, UserListRecylerClick clickListener) {
            super(itemView);
            username = itemView.findViewById(R.id.username);
            email = itemView.findViewById(R.id.email);
            mClickListener = clickListener;
            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            mClickListener.onUserClicked(getAdapterPosition());
        }
    }

    public interface UserListRecylerClick {
        void onUserClicked(int position);
    }

}
















