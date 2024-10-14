///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.6.3
//DEPS com.github.kwhat:jnativehook:2.2.2
//DEPS io.quarkus:quarkus-bom:${quarkus.version:3.15.1}@pom
//DEPS io.quarkiverse.langchain4j:quarkus-langchain4j-openai:RELEASE
//DEPS io.quarkus:quarkus-picocli
//Q:CONFIG quarkus.banner.enabled=false
//Q:CONFIG quarkus.log.level=INFO
//Q:CONFIG quarkus.langchain4j.openai.api-key=${OPENAI_API_KEY}
//JAVAC_OPTIONS -parameters

//Needed to avoid that macosx pops up icon in desktop.
//JAVA_OPTIONS -Dapple.awt.UIElement=true

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

@ActivateRequestContext
@Command(name = "lucy", mixinStandardHelpOptions = true, version = "lucy 0.1", description = "lucy made with jbang")
class lucy implements Callable<Integer> {

    @Inject
    LucyAi lucyAi;

    @Override
    public Integer call() throws Exception {
        var listener = new KeyboardListener(lucyAi);
        listener.register();
        System.out.println("Press ctrl+c to exit");
        // Keep the program running until ctrl+c is pressed
        while (true) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        // listener.stopListener();
        // return 0;
    }

    public static class KeyboardListener implements NativeKeyListener {

        Robot robot;
        boolean listening = false;
        long lastTypedTime = 0;
        static final long TYPING_COOLDOWN = 1000; // 500 ms cooldown

        StringBuffer buffer = new StringBuffer();
        private lucy.LucyAi lucyAi;

        KeyboardListener(LucyAi lucyAi) {
            this.lucyAi = lucyAi;
        }

        public void register() {

            try {
                robot = new Robot();
                robot.setAutoDelay(0);
            } catch (AWTException e) {
                throw new IllegalStateException("Could not initialize robot.", e);
            }

            try {
                GlobalScreen.registerNativeHook();
            } catch (NativeHookException e) {
                throw new IllegalStateException("Could not register key listener. Check if you have permissions.", e);
            }

            GlobalScreen.addNativeKeyListener(this);

        }

        public void unregister() throws NativeHookException {
            GlobalScreen.unregisterNativeHook();
        }

        @Override
        public void nativeKeyPressed(NativeKeyEvent nativeEvent) {
            // System.out.println("KeyPressed: " +
            // NativeKeyEvent.getKeyText(nativeEvent.getKeyCode()));
        }

        @Override
        public void nativeKeyReleased(NativeKeyEvent nativeEvent) {
            // System.out.println("KeyReleased: " +
            // NativeKeyEvent.getKeyText(nativeEvent.getKeyCode()));

        }

        @Override
        public void nativeKeyTyped(NativeKeyEvent nativeEvent) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastTypedTime < TYPING_COOLDOWN) {
                return; // Ignore events during cooldown period
            }

            if (listening) {
                buffer.append(nativeEvent.getKeyChar());
                if (nativeEvent.getKeyChar() == '/') {
                    stopListening();
                }
            } else if (nativeEvent.getKeyChar() == '/') {
                startListening();
                buffer.append(nativeEvent.getKeyChar());
            } else {
                // do nothing as not listening.
            }
        }

        private void stopListening() {
            listening = false;
            String text = buffer.toString();
            System.out.println("Listening stopped with buffer: " + buffer.toString());
            buffer = new StringBuffer();
            
            // delete the original text
            for (int i = 0; i < text.length(); i++) {
                robot.keyPress(KeyEvent.VK_BACK_SPACE);
                robot.keyRelease(KeyEvent.VK_BACK_SPACE);
            }

            typeStringWithPaste(robot, lucyAi.answer(text));
            System.out.println("Typed: " + lucyAi.answer(text));
        }

        private void startListening() {
            listening = true;
        }

        void typeStringWithPaste(Robot robot, String text) {
            var stringSelection = new StringSelection(text);
            var clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            var before = clipboard.getContents(null);
            try {
                clipboard.setContents(stringSelection, stringSelection);

                robot.keyPress(KeyEvent.VK_META);
                robot.keyPress(KeyEvent.VK_V);
                robot.keyRelease(KeyEvent.VK_V);
                robot.keyRelease(KeyEvent.VK_META);

            } finally {
                clipboard.setContents(before, null);
            }
        }

        void typeString(Robot robot, String keys) {
            System.out.println("typing " + keys);
            lastTypedTime = System.currentTimeMillis(); // Set last typed time
            for (char c : keys.toCharArray()) {
                int keyCode = KeyEvent.getExtendedKeyCodeForChar(c);
                if (KeyEvent.CHAR_UNDEFINED == keyCode) {
                    throw new RuntimeException(
                            "Key code not found for character '" + c + "'");
                }
                robot.keyPress(keyCode);
                robot.keyRelease(keyCode);
            }

        }
    }

    @RegisterAiService
    @Singleton
    public interface LucyAi {
        @SystemMessage("""
                        You are a helpful assistant.
                        A user is using his computer and needs help with typing.

                    You will be given a question or a phrase that the user wants to complete.
                If there is a ? at the end its a question if not it is something they want
                to have completed.

                Please use the format similar to what the user is typing.
                Do attempt to use line breaks and spacing to not make the text too wide,
                preferably keep it under 120 characters.
                        """)
        @UserMessage("${input}")
        String answer(String input);
    }
}
