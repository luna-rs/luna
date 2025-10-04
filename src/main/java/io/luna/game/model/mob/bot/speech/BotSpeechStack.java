package io.luna.game.model.mob.bot.speech;

import io.luna.game.model.mob.bot.Bot;
import io.luna.game.model.mob.bot.speech.BotGeneralSpeechPool.GeneralSpeech;

import java.util.ArrayDeque;
import java.util.Queue;

public class BotSpeechStack {

    private final Bot bot;
    private boolean disableGeneral;
    private boolean disableInjection;
    private boolean disableAll;// stop talking completely
    private final Queue<BotSpeech> buffer = new ArrayDeque<>(5);

    // todo simply select a random phrase to say, can be set to periodically randomlly say stuff (a stack with a max of
    // like 5 things queued) then you can force the stack to pop and say something right away otherwise regenerates stack
    // at 0

    // context aware speech? figure out how to do that. like maybe have different phrase lists
    // depending on active coordinator? ask chatgpt

    // bored.txt, happy.txt, pking.txt, minigame.txt, skilling.txt maybe even break it down even further by skill and/or by
    //feature for ease of access

    // "i love woodcutting":WoodcuttingScript

    // or if theres another way to do it thats easier

    //https://chatgpt.com/c/68da05be-c7e4-8329-81d0-a4f07966c957

    // maybe every script could return a SpeechContext that determines how and when the bot will talk
    // can add messages to bots stack with push

    // speech stack does not need to be persisted.
    // speech stack cleared on script change
    // if bot intelligence > 0.8, dont ever default to chat filler
    // higher bot intelligence, less likely to talk from general pool. more contextual reponses
    // higher kindness, more likely to say gz
    // stack only evaluated one entry per tick like bot scripts
    // by default, pushes to front, retrieves from tail. injector pushes to tail.
    public void push(BotSpeech speech) { // difference between this and output.chat is that this is integrated within
        // the bots automatic speaking pattern
// and will ensure AI generated speech patterns won't interfere with player requests for a bot to speak
        // regular push means bot will say it sometime in the future
        // high priority push means bot will say it right away (goes to front of stack), always said on following tick (or whenever processing happens)
        pushHead(speech);
    }

    // if recently spoke too much ignore processing for tick
    // push to front of stack (executed last)
    public void pushHead(BotSpeech speech) {

    }

    // push to tail of stack (executed next)
    public void pushTail(BotSpeech speech) {

    }

    public void pop() {

    }
speech.disableGeneral()     // disables generic chatter pool entirely
        speech.disableInjector()    // disables contextual responses (gz, loot, death)
        speech.disableProcessing()  // disables EVERYTHING (manual .chat only)

    public void poke() {
        // Urges this bot to type and say something. Won't always result in something being said right away.
        // but increases the chance of something being said in the near future, basically queues a generic piece of
        // speech to be said in the near future
        // chance and what they say is based on social and intelligence score
        // only if buffer isn't full already
        boolean intelligent = true;
        String phrase = generalSpeechPool.take(intelligent ? GeneralSpeech.RUNESCAPE_CULTURE :
                GeneralSpeech.CHAT_FILLER);
    }

    public void clear() {

    }
}
