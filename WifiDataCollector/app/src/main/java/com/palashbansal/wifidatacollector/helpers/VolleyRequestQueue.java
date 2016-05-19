package com.palashbansal.wifidatacollector.helpers;

import android.content.Context;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;

/**
 * Created by Palash on 3/6/2016.
 */

public class VolleyRequestQueue {
	private static RequestQueue requestQueue;
	private static Context context;

	public static RequestQueue getRequestQueue() {
		if (requestQueue == null) {
			requestQueue = Volley.newRequestQueue(context.getApplicationContext());
		}
		return requestQueue;
	}

	public static <T> void addToRequestQueue(Request<T> req, Context context) {
		if(VolleyRequestQueue.context==null)
			VolleyRequestQueue.context = context;
		getRequestQueue().add(req);
	}
}