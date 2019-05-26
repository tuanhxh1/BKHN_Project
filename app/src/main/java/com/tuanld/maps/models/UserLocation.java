package com.tuanld.maps.models;

import android.os.Parcel;
import android.os.Parcelable;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.ServerTimestamp;
import java.util.Date;

public class UserLocation implements Parcelable {
    private GeoPoint mGeoPoint;
    private @ServerTimestamp Date mTimestamp;
    private User user;

    public UserLocation(GeoPoint mGeoPoint, Date mTimestamp, User user) {
        this.mGeoPoint = mGeoPoint;
        this.mTimestamp = mTimestamp;
        this.user = user;
    }

    public UserLocation() {

    }

    protected UserLocation(Parcel in) {
        user = in.readParcelable(User.class.getClassLoader());
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(user, flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<UserLocation> CREATOR = new Creator<UserLocation>() {
        @Override
        public UserLocation createFromParcel(Parcel in) {
            return new UserLocation(in);
        }

        @Override
        public UserLocation[] newArray(int size) {
            return new UserLocation[size];
        }
    };

    public GeoPoint getmGeoPoint() {
        return mGeoPoint;
    }

    public void setmGeoPoint(GeoPoint mGeoPoint) {
        this.mGeoPoint = mGeoPoint;
    }

    public Date getmTimestamp() {
        return mTimestamp;
    }

    public void setmTimestamp(Date mTimestamp) {
        this.mTimestamp = mTimestamp;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    @Override
    public String toString() {
        return "UserLocation{" +
                "geo_point=" + mGeoPoint +
                ", timestamp= '" + mTimestamp + '\'' +
                ", user= " + user +
                '}';
    }

}
