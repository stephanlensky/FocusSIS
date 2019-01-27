package com.slensky.focussis.data.network;

import android.content.Context;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageRequest;
import com.android.volley.toolbox.StringRequest;
import com.google.gson.Gson;
import com.slensky.focussis.FocusApplication;
import com.slensky.focussis.data.focus.Absences;
import com.slensky.focussis.data.focus.Address;
import com.slensky.focussis.data.focus.Calendar;
import com.slensky.focussis.data.focus.CalendarEvent;
import com.slensky.focussis.data.focus.CalendarEventDetails;
import com.slensky.focussis.data.focus.Course;
import com.slensky.focussis.data.focus.Demographic;
import com.slensky.focussis.data.focus.FinalGrades;
import com.slensky.focussis.data.focus.FinalGradesPage;
import com.slensky.focussis.data.focus.FocusPreferences;
import com.slensky.focussis.data.focus.PasswordResponse;
import com.slensky.focussis.data.focus.Portal;
import com.slensky.focussis.data.focus.Referrals;
import com.slensky.focussis.data.focus.Schedule;
import com.slensky.focussis.data.focus.Student;
import com.slensky.focussis.data.network.model.AbsencesParser;
import com.slensky.focussis.data.network.model.AddressParser;
import com.slensky.focussis.data.network.model.CalendarEventParser;
import com.slensky.focussis.data.network.model.CalendarParser;
import com.slensky.focussis.data.network.model.CourseParser;
import com.slensky.focussis.data.network.model.DemographicParser;
import com.slensky.focussis.data.network.model.FinalGradesPageParser;
import com.slensky.focussis.data.network.model.FinalGradesParser;
import com.slensky.focussis.data.network.model.FocusParseException;
import com.slensky.focussis.data.network.model.PasswordResponseParser;
import com.slensky.focussis.data.network.model.PortalParser;
import com.slensky.focussis.data.network.model.PreferencesParser;
import com.slensky.focussis.data.network.model.ReferralsParser;
import com.slensky.focussis.data.network.model.ScheduleParser;
import com.slensky.focussis.data.network.model.StudentParser;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.CookieManager;
import java.net.HttpCookie;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.inject.Inject;

public class FocusNetApi implements FocusApi {
    private static final String TAG = "FocusNetApi";

    @Inject
    RequestQueue requestQueue;
    @Inject
    CookieManager cookieManager;
    @Inject
    Gson gson;

    public interface Listener<T> {
        /** Called when a response is received. */
        void onResponse(T response);
    }

    private boolean hasAccessedStudentPage; // api access requires first sending GET to student url
    protected Student student;
    private boolean hasAccessedFinalGradesPage; // api access requires first sending GET to final grades url
    private FinalGradesPage finalGradesPage;

    private boolean loggedIn = false;

    @SuppressWarnings("FieldCanBeLocal")
    private long sessionLengthMillis = 20 * 60 * 1000; // milliseconds

    private long sessionTimeout;

    public FocusNetApi(Context context) {
        ((FocusApplication) context.getApplicationContext()).getComponent().inject(this);
    }

    private void updateSessionTimeout() {
        this.sessionTimeout = System.currentTimeMillis() + this.sessionLengthMillis;
    }

    public Request login(final String username, final String password, final FocusApi.Listener<Boolean> listener, final Response.ErrorListener errorListener) {
        hasAccessedStudentPage = false;
        hasAccessedFinalGradesPage = false;
        StringRequest loginRequest = new StringRequest(
                Request.Method.POST, FocusEndpoints.getLoginEndpoint(), response -> {
                    boolean success = false;
                    try {
                        JSONObject responseJson = new JSONObject(response);
                        success = responseJson.getBoolean("success");
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing login response as JSON");
                        e.printStackTrace();
                    }

                    if (success) {
                        loggedIn = true;
                        updateSessionTimeout();
                    }

                    listener.onResponse(success);
                }, errorListener){
            @Override
            protected Map<String,String> getParams(){
                Map<String,String> params = new HashMap<>();
                params.put("login" , "true");
                params.put("data", "username=" + username + "&password=" + password);
                return params;
            }

        };

        queueRequest(loginRequest);
        return loginRequest;
    }

    public Request logout(final FocusApi.Listener<Boolean> listener, final Response.ErrorListener errorListener) {
        StringRequest logoutRequest = new StringRequest(
                Request.Method.POST, FocusEndpoints.getLogoutEndpoint(), response -> {
                    loggedIn = false;
                    listener.onResponse(true);
                }, errorListener);

        queueRequest(logoutRequest);
        return logoutRequest;
    }

    public Request getPortal(final FocusApi.Listener<Portal> listener, final Response.ErrorListener errorListener) {
        StringRequest portalRequest = new StringRequest(
                Request.Method.GET, FocusEndpoints.getPortalEndpoint(), response -> {
                    PortalParser portalParser = new PortalParser();
                    listener.onResponse(portalParser.parse(response));
                }, errorListener);

        queueRequest(portalRequest);
        return portalRequest;
    }

    public Request getCourse(final String id, final FocusApi.Listener<Course> listener, final Response.ErrorListener errorListener) {
        StringRequest courseRequest = new StringRequest(
                Request.Method.GET, FocusEndpoints.getCourseEndpoint(id), response -> {
                    CourseParser courseParser = new CourseParser();
                    listener.onResponse(courseParser.parse(response));
                }, errorListener);

        queueRequest(courseRequest);
        return courseRequest;
    }

    public Request getSchedule(final FocusApi.Listener<Schedule> listener, final Response.ErrorListener errorListener) {
        StringRequest scheduleRequest = new StringRequest(
                Request.Method.GET, FocusEndpoints.getScheduleEndpoint(), response -> {
                    ScheduleParser scheduleParser = new ScheduleParser();
                    listener.onResponse(scheduleParser.parse(response));
                }, errorListener);

        queueRequest(scheduleRequest);
        return scheduleRequest;
    }

    public Request getCalendar(int year, int month, final FocusApi.Listener<Calendar> listener, final Response.ErrorListener errorListener) {
        StringRequest calendarRequest = new StringRequest(
                Request.Method.GET, FocusEndpoints.getCalendarEndpoint(month, year), response -> {
                    CalendarParser calendarParser = new CalendarParser();
                    listener.onResponse(calendarParser.parse(response));
                }, errorListener);

        queueRequest(calendarRequest);
        return calendarRequest;
    }

    // alternative to assignment is event
    public Request getCalendarEvent(final String id, final CalendarEvent.EventType eventType, final FocusApi.Listener<CalendarEventDetails> listener, final Response.ErrorListener errorListener) {
        String url = eventType.equals(CalendarEvent.EventType.ASSIGNMENT)
                ? FocusEndpoints.getCalendarAssignmentEndpoint(id)
                : FocusEndpoints.getCalendarEventEndpoint(id);
        StringRequest eventRequest = new StringRequest(
                Request.Method.GET, url, response -> {
                    CalendarEventParser calendarEventParser = new CalendarEventParser();
                    calendarEventParser.setId(id);
                    calendarEventParser.setType(eventType);
                    CalendarEventDetails parsed = calendarEventParser.parse(response);
                    listener.onResponse(parsed);
                }, errorListener);

        queueRequest(eventRequest);
        return eventRequest;
    }

    private void ensureStudentPage(final Runnable onResponse, final Response.ErrorListener errorListener) {
        if (!hasAccessedStudentPage) {
            Log.d(TAG, "Retrieving student page for first time");
            final StringRequest studentRequest = new StringRequest(Request.Method.GET, FocusEndpoints.getStudentEndpoint(), response -> {
                final StudentParser studentParser = new StudentParser();
                try {
                    student = studentParser.parse(response);

                    ImageRequest imageRequest = new ImageRequest(student.getPictureUrl(),
                            bitmap -> {
                                student.setPicture(bitmap);
                                hasAccessedStudentPage = true;
                                onResponse.run();
                            }, 0, 0, null,
                            error -> {
                                Log.w(TAG, "Network error fetching image, continuing anyway");
                                hasAccessedStudentPage = true;
                                onResponse.run();
                            });
                    queueRequest(imageRequest);
                } catch (JSONException e) {
                    Log.e(TAG, "JSONException while parsing student page");
                    errorListener.onErrorResponse(new VolleyError(e.toString()));
                    throw new RuntimeException(e);
                }
            }, errorListener);
            queueRequest(studentRequest);
        } else {
            onResponse.run();
        }
    }

    public void getDemographic(final FocusApi.Listener<Demographic> listener, final Response.ErrorListener errorListener) {
        ensureStudentPage(() -> {
            Map<String, String> editController = student.getMethods().get("EditController");
            if (editController == null) {
                throw new FocusParseException("EditController not found in student page");
            }
            final String csrfToken = editController.get("getFieldData");

            final MultipartRequest demographicRequest = new MultipartRequest(
                    Request.Method.POST, student.getApiUrl(), response -> {
                        DemographicParser demographicParser = new DemographicParser();
                        String responseStr = new String(response.data);
                        try {
                            Demographic demographic = demographicParser.parse(responseStr);
                            demographic.setStudent(student);
                            listener.onResponse(demographic);
                        } catch (JSONException e) {
                            Log.d(TAG, responseStr);
                            Log.e(TAG, "JSONException while parsing demographic");
                            errorListener.onErrorResponse(new VolleyError(e.toString()));
                            throw new RuntimeException(e);
                        }
                    }, errorListener) {
                @Override
                protected Map<String, String> getParams() {
                    Map<String, String> params = new HashMap<>();
                    String req = "{\"requests\":[" +
                            "{\"controller\":\"EditController\",\"method\":\"cache:getFieldData\",\"token\":\"" + csrfToken + "\",\"args\":[\"general\",\"SISStudent\",%s]}," +
                            "{\"controller\":\"EditController\",\"method\":\"cache:getFieldData\",\"token\":\"" + csrfToken + "\",\"args\":[\"9\",\"SISStudent\",%<s]}," +
                            "{\"controller\":\"EditController\",\"method\":\"cache:getFieldData\",\"token\":\"" + csrfToken + "\",\"args\":[\"6\",\"SISStudent\",%<s]}" +
                            "]}";
                    Log.d(TAG,String.format(req, student.getId()));

                    params.put("__call__", String.format(req, student.getId()));
                    return params;
                }
            };
            queueRequest(demographicRequest);
        }, errorListener);
    }

    public void getAddress(final FocusApi.Listener<Address> listener, final Response.ErrorListener errorListener) {
        ensureStudentPage(() -> {
            Map<String, String> addressController = student.getMethods().get("EditController");
            if (addressController == null) {
                throw new FocusParseException("AddressController not found in student page");
            }
            final String csrfTokenAddresses = addressController.get("getAddresses");
            final String csrfTokenContacts = addressController.get("getContacts");

            final MultipartRequest addressRequest = new MultipartRequest(
                    Request.Method.POST, student.getApiUrl(), response -> {
                        AddressParser addressParser = new AddressParser();
                        String responseStr = new String(response.data);
                        try {
                            Address address = addressParser.parse(responseStr);
                            address.setStudent(student);
                            listener.onResponse(address);
                        } catch (JSONException e) {
                            Log.e(TAG, "JSONException while parsing address");
                            errorListener.onErrorResponse(new VolleyError(e.toString()));
                            throw new RuntimeException(e);
                        }
                    }, errorListener) {
                @Override
                protected Map<String, String> getParams() {
                    Map<String, String> params = new HashMap<>();
                    String req = "{\"requests\":[" +
                            "{\"controller\":\"AddressController\",\"method\":\"getAddresses\",\"token\":\"" + csrfTokenAddresses + "\",\"args\":[%s]}," +
                            "{\"controller\":\"AddressController\",\"method\":\"getContacts\",\"token\":\"" + csrfTokenContacts + "\",\"args\":[%<s]}" +
                            "]}";
                    Log.d(TAG, String.format(req, student.getId()));

                    params.put("__call__", String.format(req, student.getId()));
                    return params;
                }
            };
            queueRequest(addressRequest);
        }, errorListener);
    }

    public Request getReferrals(final FocusApi.Listener<Referrals> listener, final Response.ErrorListener errorListener) {
        StringRequest referralsRequest = new StringRequest(
                Request.Method.GET, FocusEndpoints.getReferralsEndpoint(), response -> {
                    ReferralsParser referralsParser = new ReferralsParser();
                    listener.onResponse(referralsParser.parse(response));
                }, errorListener);

        queueRequest(referralsRequest);
        return referralsRequest;
    }

    public Request getAbsences(final FocusApi.Listener<Absences> listener, final Response.ErrorListener errorListener) {
        StringRequest absencesRequest = new StringRequest(
                Request.Method.GET, FocusEndpoints.getAbsencesEndpoint(), response -> {
                    AbsencesParser absencesParser = new AbsencesParser();
                    listener.onResponse(absencesParser.parse(response));
                }, errorListener);

        queueRequest(absencesRequest);
        return absencesRequest;
    }

    private void ensureFinalGradesPage(final StringRequest nextRequest) {
        if (!hasAccessedFinalGradesPage) {
            Log.d(TAG, "Retrieving final grades page for first time");
            final StringRequest finalGradesRequest = new StringRequest(Request.Method.GET, FocusEndpoints.getFinalGradesEndpoint(), response -> {
                final FinalGradesPageParser finalGradesPageParser = new FinalGradesPageParser();
                finalGradesPage = finalGradesPageParser.parse(response);
                hasAccessedFinalGradesPage = true;
                queueRequest(nextRequest);
            }, nextRequest.getErrorListener());
            queueRequest(finalGradesRequest);
        }
        else {
            queueRequest(nextRequest);
        }
    }

    private void signRequest(Map<String, String> request, String secret) throws NoSuchAlgorithmException, InvalidKeyException {
        String digest = String.format("-%s-%s-%s", request.get("accessID"), request.get("api"), request.get("method"));
        SecretKeySpec signingKey = new SecretKeySpec(secret.getBytes(), "HmacSHA1");
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(signingKey);

        Formatter formatter = new Formatter();
        for (byte b : mac.doFinal(digest.getBytes())) {
            formatter.format("%02x", b);
        }
        request.put("signature", formatter.toString());
    }

    public Request getFinalGrades(final FocusApi.FinalGradesType type, final FocusApi.Listener<FinalGrades> listener, final Response.ErrorListener errorListener) {
        Log.d(TAG, "Retrieving final grades type " + type.name());
        StringRequest finalGradesRequest = new StringRequest(
                Request.Method.POST, FocusEndpoints.getLegacyApiEndpoint(), response -> {
                    FinalGradesParser finalGradesParser = new FinalGradesParser();
                    FinalGrades parsed = finalGradesParser.parse(response);
                    parsed.setFinalGradesPage(finalGradesPage);
                    String s = gson.toJson(parsed);
                    final int chunkSize = 2048;
                    for (int i = 0; i < s.length(); i += chunkSize) {
                        Log.d(TAG, s.substring(i, Math.min(s.length(), i + chunkSize)));
                    }
                    listener.onResponse(parsed);
                }, errorListener) {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new LinkedHashMap<>();
                params.put("accessID", finalGradesPage.getStudentId());
                params.put("api", "finalGrades");
                params.put("method", "requestGrades");
                params.put("modname", "Grades/StudentRCGrades.php");
                String argKey = "arguments[]";
                switch (type) {
                    case COURSE_HISTORY:
                        params.put(argKey, "-1");
                        break;
                    case CURRENT_SEMESTER:
                        params.put(argKey, finalGradesPage.getCurrentSemesterTargetMarkingPeriod());
                        break;
                    case CURRENT_SEMESTER_EXAMS:
                        params.put(argKey, finalGradesPage.getCurrentSemesterExamsTargetMarkingPeriod());
                        break;
                    case ALL_SEMESTERS:
                        params.put(argKey, "all_SEM");
                        break;
                    case ALL_SEMESTERS_EXAMS:
                        params.put(argKey, "all_SEM_exams");
                        break;
                }
                params.put("arguments[1][**FIRST-REQUEST**]", "true");
                try {
                    signRequest(params, finalGradesPage.getHmacSecret());
                } catch (NoSuchAlgorithmException e) {
                    Log.e(TAG, "Could not find HmacSHA1 algorithm for signing final grades request");
                } catch (InvalidKeyException e) {
                    Log.e(TAG, "Invalid key for HmacSHA1 algorithm while signing final grades request");
                }
                return  params;
            }
        };

        ensureFinalGradesPage(finalGradesRequest);
        return finalGradesRequest;
    }

    public Request getPreferences(final FocusApi.Listener<FocusPreferences> listener, final Response.ErrorListener errorListener) {
        StringRequest preferencesRequest = new StringRequest(
                Request.Method.GET, FocusEndpoints.getPreferencesEndpoint(), response -> {
                    PreferencesParser preferencesParser = new PreferencesParser();
                    listener.onResponse(preferencesParser.parse(response));
                }, errorListener);

        queueRequest(preferencesRequest);
        return preferencesRequest;
    }

    public Request setPreferences(final FocusPreferences preferences, final FocusApi.Listener<FocusPreferences> listener, final Response.ErrorListener errorListener) {
        StringRequest preferencesRequest = new StringRequest(
                Request.Method.POST, FocusEndpoints.getPreferencesEndpoint(), response -> {
                    PreferencesParser preferencesParser = new PreferencesParser();
                    listener.onResponse(preferencesParser.parse(response));
                }, errorListener) {
            @Override
            protected Map<String, String> getParams() {
                Map<String,String> params = new HashMap<>();
                if (preferences.isEnglishLanguage()) {
                    params.put("values[Preferences][LANGUAGE]", "en_US");
                    params.put("btn_save", "Save");
                }
                return params;
            }
        };

        queueRequest(preferencesRequest);
        return preferencesRequest;
    }

    public Request changePassword(final String currentPassword, final String newPassword, final String verifyNewPassword, final FocusApi.Listener<PasswordResponse> listener, final Response.ErrorListener errorListener) {
        StringRequest passwordRequest = new StringRequest(
                Request.Method.POST, FocusEndpoints.getPasswordChangeEndpoint(), response -> {
                    PasswordResponseParser passwordParser = new PasswordResponseParser();
                    listener.onResponse(passwordParser.parse(response));
                }, errorListener) {
            @Override
            protected Map<String, String> getParams() {
                Map<String,String> params = new HashMap<>();
                params.put("values[current]", currentPassword);
                params.put("values[verify]", verifyNewPassword);
                params.put("values[new]", newPassword);
                params.put("btn_save", "Save");
                return params;
            }
        };

        queueRequest(passwordRequest);
        return passwordRequest;
    }

    public boolean isSessionExpired() {
        return sessionTimeout <= System.currentTimeMillis();
    }

    public long getSessionTimeout() {
        return sessionTimeout;
    }

    public void setSessionTimeout(long sessionTimeout) {
        this.sessionTimeout = sessionTimeout;
    }

    public boolean isLoggedIn() {
        return loggedIn;
    }

    public void setLoggedIn(boolean loggedIn) {
        this.loggedIn = loggedIn;
    }

    public boolean hasSession() {
        for (HttpCookie c : RequestSingleton.getCookieManager().getCookieStore().getCookies()) {
            if (c.getName().equals("PHPSESSID")) {
                return true;
            }
        }
        return false;
    }

    private void queueRequest(Request request) {
        Log.d(TAG, "Queuing " + request.getUrl());
        // necessary for http monitoring (debug only)
//        for (int i = 0; i < cookieManager.getCookieStore().getCookies().size(); i++) {
//            cookieManager.getCookieStore().getCookies().get(0).setSecure(false);
//        }

        //noinspection unchecked
        requestQueue.add(request);
    }

    public void cancelAll(final RequestQueue.RequestFilter filter) {
        requestQueue.cancelAll(filter);
    }
}
