package com.tuanld.maps.models;

import com.google.android.gms.maps.model.Polyline;
import com.google.maps.model.DirectionsLeg;

public class PolylineData {
    private Polyline mPolyline;
    private DirectionsLeg mLeg;

    public PolylineData(Polyline mPolyline, DirectionsLeg mLeg) {
        this.mPolyline = mPolyline;
        this.mLeg = mLeg;
    }

    public Polyline getmPolyline() {
        return mPolyline;
    }

    public void setmPolyline(Polyline mPolyline) {
        this.mPolyline = mPolyline;
    }

    public DirectionsLeg getmLeg() {
        return mLeg;
    }

    public void setmLeg(DirectionsLeg mLeg) {
        this.mLeg = mLeg;
    }

    @Override
    public String toString() {
        return "PolylineData{" +
                "polyline=" + mPolyline +
                ", leg=" + mLeg +
                '}';
    }
}
