package com.ftechz.DebatingTimer;

import java.util.*;

/**
 * Debate class
 * In charge of the flow of stages (speakers) in the debate
 */
public class Debate
{
    public enum DebateStatus
    {
        setup,
        speaking,
        paused,
        transitioning,
        finished
    }

    //
    // Members
    private LinkedList<AlarmChain> mStages;
    private AlarmChain mCurrentStage;
    private Iterator<AlarmChain> mStageIterator;
    private SpeakersManager mSpeakersManager;
    private Timer mTickTimer;

    private AlertManager mAlertManager;
    private HashMap<String, AlarmChain.AlarmChainAlert[]> mAlertSets;

    //
    // Methods
    public Debate(AlertManager alertManager)
    {
        mAlertManager = alertManager;
        mStages = new LinkedList<AlarmChain>();
        mAlertSets = new HashMap<String, AlarmChain.AlarmChainAlert[]>();
        mStageIterator = mStages.iterator();
        mSpeakersManager = new SpeakersManager();
        mTickTimer = new Timer();
    }

    /**
     * Add a stage to the debate
     * Has to be added in the order of the debate
     *
     * @param alarmChain
     * @param alarmSetName  The name of the alarmset specified when added to the debate
     * @return
     */
    public boolean addStage(AlarmChain alarmChain, String alarmSetName)
    {
        if(mAlertSets.containsKey(alarmSetName))
        {
            alarmChain.addTimes(mAlertSets.get(alarmSetName));

            if(alarmChain.getClass() == SpeakerTimer.class)
            {
                SpeakerTimer speakerTimer = (SpeakerTimer) alarmChain;
                speakerTimer.setSpeakersManager(mSpeakersManager);
            }

            mStages.add(alarmChain);

            mStageIterator = mStages.iterator();
            return true;
        }
        return false;
    }

    /**
     * Add an alarm set that will be used in this debate
     *
     * @param name
     * @param alarmSet
     */
    public void addAlarmSet(String name, AlarmChain.AlarmChainAlert[] alarmSet)
    {
        for(AlarmChain.AlarmChainAlert alarm : alarmSet)
        {
            alarm.setAlertManager(mAlertManager);
        }

        mAlertSets.put(name, alarmSet);
    }

    /**
     * Add a speaker to the debate
     * Will be used to allow grabbing of information about speakers later...
     * @param speaker
     */
    public void addSpeaker(Speaker speaker, int team, boolean leader)
    {
        mSpeakersManager.addSpeaker(speaker, team, leader);
    }

    public void setSides(int team, SpeakersManager.SpeakerSide side)
    {
        mSpeakersManager.setSides(team, side);
    }

    public boolean prepareNextSpeaker()
    {
        if(mStageIterator.hasNext())
        {
            if(mCurrentStage != null)
            {
                mCurrentStage.cancel();
            }
            mCurrentStage = mStageIterator.next();
            return true;
        }
        else
        {
            mCurrentStage = null;
        }

        return false;
    }

    public void startNextSpeaker()
    {
        if(mCurrentStage != null)
        {
            mTickTimer.purge();
            mCurrentStage.start(mTickTimer);
            mAlertManager.hideNotification();   // Hide if already showing
            mAlertManager.showNotification(mCurrentStage);
        }
    }

    public void stop()
    {
        if(getDebateStatus() == DebateStatus.speaking)
        {
            if (mCurrentStage != null)
            {
                mAlertManager.hideNotification();
                mCurrentStage.cancel();
            }
        }
    }

    public boolean start()
    {
        if((getDebateStatus() == DebateStatus.setup) ||
                (getDebateStatus() == DebateStatus.transitioning))
        {
            if(prepareNextSpeaker())
            {
                startNextSpeaker();
                return true;
            }
        }
        return false;
    }

    public DebateStatus getDebateStatus() {
        if(mCurrentStage != null)
        {
            switch (mCurrentStage.getRunningState())
            {
                case Running:
                    return DebateStatus.speaking;
                case Paused:
                    return DebateStatus.paused;
                case Finished:
                    if(!mStageIterator.hasNext())
                    {
                        return DebateStatus.finished;
                    }
                case Stopped:
                default:
                    return DebateStatus.transitioning;
            }
        }
        else
        {
            return DebateStatus.setup;
        }
    }

    public String getTitleText()
    {
        if(mCurrentStage != null)
        {
            return mCurrentStage.getTitleText();
        }
        return "";
    }

    public long getStageCurrentTime()
    {
        if(mCurrentStage != null)
        {
            return mCurrentStage.getSeconds();
        }
        else
        {
            return 0;
        }
    }

    public long getStageNextTime()
    {
        if(mCurrentStage != null)
        {
            return mCurrentStage.getNextTime();
        }
        else
        {
            return 0;
        }
    }

    public long getStageFinalTime()
    {
        if(mCurrentStage != null)
        {
            return mCurrentStage.getFinalTime();
        }
        else
        {
            return 0;
        }
    }
    
    public String getStageStateText()
    {
        if(mCurrentStage != null) {
            return mCurrentStage.getStateText();
        } else {
            return "";
        }
    }

    public void reset()
    {
        // Stop current stage timer, if on
        if(mCurrentStage != null)
        {
            mCurrentStage.cancel();
        }
        mCurrentStage = null;
        mTickTimer.purge();
        mTickTimer.cancel();
        mTickTimer = new Timer();

        mAlertManager.hideNotification();

        ListIterator<AlarmChain> stageIterator = mStages.listIterator();
        while(stageIterator.hasNext())
        {
            AlarmChain stage = stageIterator.next();
            stage.cancel();
            stageIterator.set(stage.newCopy());
        }

        mStageIterator = mStages.iterator();
    }

    public void release()
    {
        if(mAlertManager != null)
        {
            mAlertManager.hideNotification();
        }

        mCurrentStage = null;

        mTickTimer.cancel();
        mTickTimer.purge();
        mTickTimer = null;

        mStages = null;
    }

    public void resume()
    {
        if(mCurrentStage != null)
        {
            mCurrentStage.resume();
        }
    }

    public void pause()
    {
        if(mCurrentStage != null)
        {
            mCurrentStage.pause();
        }
    }

}
