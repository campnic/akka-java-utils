package com.github.campnic;

import akka.actor.*;
import akka.japi.Procedure;
import scala.concurrent.duration.Deadline;
import scala.concurrent.duration.FiniteDuration;

import java.util.ArrayList;
import java.util.List;

public class SelectionAsker extends UntypedActor {

    public static Props mkprops(){
        return Props.create(SelectionAsker.class);
    }

    public interface AskSelection{
        public ActorSelection getSelection(UntypedActorContext ref);
        public Object getMsg();
        public FiniteDuration getTimeoutDuration();
    }

    private abstract static class AskBaseSelection implements AskSelection {
        private final Object mMsg;
        private final FiniteDuration mTimeoutDuration;

        public AskBaseSelection(Object mMsg, FiniteDuration mTimeoutDuration) {
            this.mMsg = mMsg;
            this.mTimeoutDuration = mTimeoutDuration;
        }

        @Override
        public Object getMsg() {
            return mMsg;
        }

        @Override
        public FiniteDuration getTimeoutDuration() {
            return mTimeoutDuration;
        }
    }

    public static class AskStringSelection extends AskBaseSelection{
        private final String mSelectionQuery;

        public AskStringSelection(String mSelectionQuery, Object msg, FiniteDuration timeout){
            super(msg, timeout);
            this.mSelectionQuery = mSelectionQuery;
        }

        @Override
        public ActorSelection getSelection(UntypedActorContext ref) {
            return ref.actorSelection(mSelectionQuery);
        }
    }

    public static class AskActorSelection extends AskBaseSelection {
        private final ActorSelection mSelection;

        public AskActorSelection(ActorSelection selection, Object msg, FiniteDuration timeout){
            super(msg, timeout);
            this.mSelection = selection;
        }

        @Override
        public ActorSelection getSelection(UntypedActorContext ref) {
            return mSelection;
        }
    }

    public static  AskSelection create(ActorSelection selection,  Object message, FiniteDuration timeout){
        return new AskActorSelection(selection, message, timeout);
    }

    public static  AskSelection create(String selectionQuery,  Object message, FiniteDuration timeout){
        return new AskStringSelection(selectionQuery, message, timeout);
    }

    private static class WaitingProcedure implements Procedure<Object> {
        final private UntypedActorContext mContext;
        final private ActorRef mOriginator;
        final private Deadline mTimeout;
        final private List<Object> responses;

        private WaitingProcedure(UntypedActorContext self, ActorRef originator, Deadline timeout) {
            this.mContext = self;
            this.mOriginator = originator;
            this.mTimeout = timeout;
            this.responses = new ArrayList<Object>();
        }

        @Override
        public void apply(Object msg) throws Exception {
            if(ReceiveTimeout.class.isAssignableFrom(msg.getClass())){
                mOriginator.tell(responses, mContext.self());
                mContext.stop(mContext.self());
            }
            else{
                this.responses.add(msg);
                mContext.setReceiveTimeout(mTimeout.timeLeft());
            }
        }
    };

    @Override
    public void onReceive(Object msg) throws Exception {
        if(AskSelection.class.isAssignableFrom(msg.getClass())){
            AskSelection ask = (AskSelection)msg;
            WaitingProcedure state = new WaitingProcedure(getContext(), getSender(), ask.getTimeoutDuration().fromNow());
            ActorSelection selection = ask.getSelection(getContext());
            getContext().setReceiveTimeout(ask.getTimeoutDuration());
            getContext().become(state);
            selection.tell(ask.getMsg(), getSelf());
        }
    }
}
