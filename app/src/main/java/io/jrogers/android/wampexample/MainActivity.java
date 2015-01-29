package io.jrogers.android.wampexample;

import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import rx.Observable;
import rx.Subscription;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.schedulers.Schedulers;
import ws.wamp.jawampa.ApplicationError;
import ws.wamp.jawampa.Request;
import ws.wamp.jawampa.WampClient;
import ws.wamp.jawampa.WampClientBuilder;
import ws.wamp.jawampa.WampError;
import ws.wamp.jawampa.WampRouter;
import ws.wamp.jawampa.WampRouterBuilder;
import ws.wamp.jawampa.transport.SimpleWampWebsocketListener;


public class MainActivity extends ActionBarActivity {

    private Handler mHandler;

    private WampRouter mRouter;
    private SimpleWampWebsocketListener mServer;
    private WampClient mClient1;
    private WampClient mClient2;

    private Subscription mEventPublication;
    private Subscription mEventSubscription;

    private EditText mParam1Text;
    private EditText mParam2Text;
    private TextView mResultText;
    private TextView mEventResultText;
    private TextView mSession1StatusText;
    private TextView mSession2StatusText;

    private static final int EVENT_INTERVAL = 2000;
    private int mLastEventValue = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mHandler = new Handler(Looper.getMainLooper());
        setContentView(R.layout.activity_main);
        mParam1Text = (EditText) findViewById(R.id.text_param_1);
        mParam2Text = (EditText) findViewById(R.id.text_param_2);
        mResultText = (TextView) findViewById(R.id.text_result);
        mSession1StatusText = (TextView) findViewById(R.id.text_session1_status);
        mSession2StatusText = (TextView) findViewById(R.id.text_session2_status);
        mEventResultText = (TextView) findViewById(R.id.text_event_result);
        Button startButton = (Button) findViewById(R.id.button_start);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                start();
            }
        });
        Button stopButton = (Button) findViewById(R.id.button_stop);
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stop();
            }
        });
        Button callButton = (Button) findViewById(R.id.button_call);
        callButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                callOperation();
            }
        });
        startRouter();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopRouter();
    }

    private void startRouter() {
        Thread t = new Thread() {
            public void run() {
                WampRouterBuilder routerBuilder = new WampRouterBuilder();
                try {
                    routerBuilder.addRealm("realm1");
                    mRouter = routerBuilder.build();
                } catch (ApplicationError e1) {
                    return;
                }

                URI serverUri = URI.create("ws://localhost:8080/ws1");
                mServer = new SimpleWampWebsocketListener(mRouter, serverUri, null);
                mServer.start();
            }
        };
        t.start();
    }

    private void start() {
        WampClientBuilder builder = new WampClientBuilder();

        // Build two clients
        try {
            builder.withUri("ws://localhost:8080/ws1")
                    .withRealm("realm1")
                    .withInfiniteReconnects()
                    .withReconnectInterval(3, TimeUnit.SECONDS);
            mClient1 = builder.build();
            mClient2 = builder.build();
        } catch (WampError e) {
            return;
        }

        mClient1.statusChanged().subscribe(new Action1<WampClient.Status>() {
            @Override
            public void call(final WampClient.Status status) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mSession1StatusText.setText("Status changed to " + status);
                        if (status == WampClient.Status.Connected) {
                            // Register a procedure
                            mClient1.registerProcedure("com.example.add").subscribe(new Action1<Request>() {
                                @Override
                                public void call(Request request) {
                                    if (request.arguments() == null || request.arguments().size() != 2
                                            || !request.arguments().get(0).canConvertToLong()
                                            || !request.arguments().get(1).canConvertToLong())
                                    {
                                        try {
                                            request.replyError(new ApplicationError(ApplicationError.INVALID_PARAMETER));
                                        } catch (ApplicationError e) {
                                            e.printStackTrace();
                                        }
                                    }
                                    else {
                                        long a = request.arguments().get(0).asLong();
                                        long b = request.arguments().get(1).asLong();
                                        request.reply(a + b);
                                    }
                                }
                            });
                        }
                    }
                });
            }
        }, new Action1<Throwable>() {
            @Override
            public void call(final Throwable t) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mSession1StatusText.setText("Session ended with error " + t);
                    }
                });
            }
        }, new Action0() {
            @Override
            public void call() {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mSession1StatusText.setText("Session ended normally");
                    }
                });
            }
        });

        mClient2.statusChanged().subscribe(new Action1<WampClient.Status>() {
            @Override
            public void call(final WampClient.Status status) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mSession2StatusText.setText("Status changed to " + status);
                        if (status == WampClient.Status.Connected) {
                            mEventSubscription = mClient2.makeSubscription("test.event", String.class)
                                    .subscribe(new Action1<String>() {
                                        @Override
                                        public void call(final String result) {
                                            mHandler.post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    mEventResultText.setText("Received event test.event with value " + result);
                                                }
                                            });
                                        }
                                    }, new Action1<Throwable>() {
                                        @Override
                                        public void call(final Throwable t) {
                                            mHandler.post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    mEventResultText.setText("Completed event test.event with error " + t);
                                                }
                                            });
                                        }
                                    }, new Action0() {
                                        @Override
                                        public void call() {
                                            mHandler.post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    mEventResultText.setText("Completed event test.event");
                                                }
                                            });
                                        }
                                    });

                            // Subscribe on the topic

                        }
                    }
                });
            }
        }, new Action1<Throwable>() {
            @Override
            public void call(final Throwable t) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mSession2StatusText.setText("Session ended with error " + t);
                    }
                });
            }
        }, new Action0() {
            @Override
            public void call() {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mSession2StatusText.setText("Session ended normally");
                    }
                });
            }
        });

        mClient1.open();
        mClient2.open();

        // Publish an event regularly
        mEventPublication = Schedulers.computation().createWorker().schedulePeriodically(new Action0() {
            @Override
            public void call() {
                mClient1.publish("test.event", "Hello " + mLastEventValue);
                mLastEventValue++;
            }
        }, EVENT_INTERVAL, EVENT_INTERVAL, TimeUnit.MILLISECONDS);
    }

    private void stop() {
        if (mEventSubscription != null) {
            mEventSubscription.unsubscribe();
        }

        if (mEventPublication != null) {
            mEventPublication.unsubscribe();
        }

        if (mClient1 != null) {
            mClient1.close();
        }

        if (mClient2 != null) {
            mClient2.close();
        }

        mEventSubscription = null;
        mEventPublication = null;
        mClient1 = null;
        mClient2 = null;
    }

    private void stopRouter() {
        if (mRouter != null) {
            mRouter.close();
        }

        if (mServer != null) {
            mServer.stop();
        }
    }

    private void callOperation() {
        if (mClient2 != null) {
            final long param1 = Long.valueOf(mParam1Text.getText().toString());
            final long param2 = Long.valueOf(mParam2Text.getText().toString());
            Observable<Long> result1 = mClient2.call("com.example.add", Long.class, param1, param2);
            result1.subscribe(new Action1<Long>() {
                @Override
                public void call(final Long result) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mResultText.setText("Completed add with result " + result);
                        }
                    });
                }
            }, new Action1<Throwable>() {
                @Override
                public void call(final Throwable error) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mResultText.setText("Completed add with error " + error);
                        }
                    });
                }
            });
        }
    }
}
