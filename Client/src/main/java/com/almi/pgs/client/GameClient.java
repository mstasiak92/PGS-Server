package com.almi.pgs.client;

import com.almi.pgs.commons.Constants;
import com.almi.pgs.game.*;
import com.almi.pgs.game.packets.AuthPacket;
import com.almi.pgs.game.packets.AuthResponsePacket;
import com.almi.pgs.game.packets.GamePacket;
import com.almi.pgs.game.packets.Packet;
import com.almi.pgs.germancoding.rudp.ReliableSocket;
import com.jme3.app.SimpleApplication;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.AnalogListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.post.FilterPostProcessor;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import com.jme3.shadow.DirectionalLightShadowFilter;
import com.jme3.shadow.DirectionalLightShadowRenderer;
import java.io.File;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static com.almi.pgs.commons.Constants.RECEIVE_BUFFER_SIZE;
import static com.almi.pgs.commons.Constants.SEND_BUFFER_SIZE;
import static com.almi.pgs.commons.Constants.SILENT_MODE;

/**
 * Created by Almi on 2016-12-10.
 */
public class GameClient extends SimpleApplication {

    private final static Logger log = LoggerFactory.getLogger(GameClient.class);

    private Geometry currentPlayerGeometry;
    private Geometry blue;
    private Boolean isRunning = false;
    private ReliableSocket server;
    private int id = -1;
    private int teamID = -1;
    /**
     * Packet manager manages all communication with server through ReliableSocket. Use this manager
     * for sending data and add packet listeners to receive certain packet
     */
    private PacketManager packetManager = new PacketManager();
    /**
     * PlayerID - Player Geometry (if need arises - change value object type to whatever needed).
     * Current player does not have entry in this map!!! use other variables!
     */
    private Map<Integer, Geometry> playerSpatials = new HashMap<>();

    public GameClient(String[] gameStringParams) {
        packetManager.addPacketListener(new ClientAuthPacketListener());
        packetManager.addPacketListener(new ClientGamePacketListener(this));
    }

    @Override
    public void simpleInitApp() {
        Box box1 = new Box(1,1,1);
        blue = new Geometry("Box", box1);
        blue.setLocalTranslation(new Vector3f(1,-1,1));
        Material mat1 = new Material(assetManager,
                "Common/MatDefs/Misc/Unshaded.j3md");
        mat1.setColor("Color", ColorRGBA.Blue);
        blue.setMaterial(mat1);

        /** create a currentPlayerGeometry box straight above the blue one at (1,3,1) */
        Box box2 = new Box(1,1,1);
        currentPlayerGeometry = new Geometry("Box", box2);
        currentPlayerGeometry.setLocalTranslation(new Vector3f(1,3,1));
        Material mat2 = new Material(assetManager,
                "Common/MatDefs/Misc/Unshaded.j3md");
        mat2.setColor("Color", ColorRGBA.Red);
        currentPlayerGeometry.setMaterial(mat2);

        rootNode.attachChild(blue);
        rootNode.attachChild(currentPlayerGeometry);

		assetManager.registerLocator(new File("Client/assets").getAbsolutePath(), FileLocator.class);

		this.setLevel();
		this.setLight();

        initKeys();
        try {
            server = new ReliableSocket();
            server.setReceiveBufferSize(RECEIVE_BUFFER_SIZE);
            server.setSendBufferSize(SEND_BUFFER_SIZE);
            server.connect(new InetSocketAddress("127.0.0.1", Constants.PORT));

        } catch (IOException e) {
            log.error(ExceptionUtils.getStackTrace(e));
        }
        new ReceiverThread().start();
    }

    @Override
    public void simpleUpdate(float tpf) {

    }

    private void initKeys() {
        // You can map one or several inputs to one named action
        inputManager.addMapping("Login",  new KeyTrigger(KeyInput.KEY_G));
        inputManager.addMapping("Pause",  new KeyTrigger(KeyInput.KEY_P));
        inputManager.addMapping("Left",   new KeyTrigger(KeyInput.KEY_J));
        inputManager.addMapping("Right",  new KeyTrigger(KeyInput.KEY_K));
        inputManager.addMapping("Rotate", new KeyTrigger(KeyInput.KEY_SPACE), new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        // Add the names to the action listener.
        inputManager.addListener(actionListener,"Pause");
        inputManager.addListener(analogListener, "Login", "Left", "Right", "Rotate");

    }

    private ActionListener actionListener = (name, keyPressed, tpf) -> {
        if (name.equals("Pause") && !keyPressed) {
            isRunning = !isRunning;
        }
    };

    private boolean isLoginRequestSent = false;
    private AnalogListener analogListener = new AnalogListener() {
        public void onAnalog(String name, float value, float tpf) {
            if (isRunning) {
                if (name.equals("Rotate")) {
                    currentPlayerGeometry.rotate(0, value*speed, 0);
                }
                if (name.equals("Right")) {
                    Vector3f v = currentPlayerGeometry.getLocalTranslation();
                    currentPlayerGeometry.setLocalTranslation(v.x + value*speed, v.y, v.z);
                }
                if (name.equals("Left")) {
                    Vector3f v = currentPlayerGeometry.getLocalTranslation();
                    currentPlayerGeometry.setLocalTranslation(v.x - value*speed, v.y, v.z);
                }

                Vector3f redTranslation = currentPlayerGeometry.getWorldTranslation();
                Quaternion redRotation    = currentPlayerGeometry.getWorldRotation();
                GamePacket movementPacket = new GamePacket(redTranslation.getX(),
                        redTranslation.getY(),
                        redTranslation.getZ(),
                        redRotation.getW(),
                        redRotation.getX(),
                        redRotation.getY(),
                        redRotation.getZ());
                movementPacket.setPlayerID((byte) id);
                movementPacket.setTeam((byte) teamID);
                packetManager.sendPacket(server, movementPacket);
            } else {
                if(name.equals("Login")) {
                    if(!isLoginRequestSent) {
                        AuthPacket authPacket = new AuthPacket("user1", "password1");
                        packetManager.sendPacket(server, authPacket);
                        log.info("Packet sent");
                    }
                } else {
                    System.out.println("Press P to unpause.");
                }
            }
        }
    };

    private class ReceiverThread extends Thread {

        public ReceiverThread() {
        }

        @Override
        public void run() {
            try {
                InputStream is = server.getInputStream();
                log.info("In here");
                while(true) {
                    receive(is);
                }
            } catch (Exception e) {
                //TODO server disconnected ... notify user on this.
                log.info(ExceptionUtils.getStackTrace(e));
            }
        }

        private void receive(InputStream is) throws Exception {
            byte[] buffer = new byte[1024];

            while(is.read(buffer) > 0) {
                String serializedPacket = new String(buffer);
//                log.info(serializedPacket);
                Packet receivedPacket = packetManager.getGeneralGamePacket(serializedPacket);
                if(!SILENT_MODE) {
                    log.info(Objects.isNull(receivedPacket) + "");
                }
                if(receivedPacket != null) {
                    packetManager.handlePacket(receivedPacket);
                }
            }
        }
    }

    /**
     * TODO : assign id for current player from received packet
     */
    private class ClientAuthPacketListener implements PacketListener {

        @Override
        public void handlePacket(Packet gamePacket) {
            if(gamePacket != null) {
                AuthResponsePacket authResponse = (AuthResponsePacket) gamePacket;
                if(!SILENT_MODE) {
                    log.info("Response code = " + authResponse.getCode());
                }
                if(authResponse.getCode() == 200) {
                    if(!SILENT_MODE) {
                        log.info("client ok");
                    }
                    id = authResponse.getPid();
                    teamID = authResponse.getTid();
                    isLoginRequestSent = true;
                    isRunning = true;
                } else {
                    //TODO proper notification for client
                    log.info(authResponse.getReason());
                }
            }
        }

        @Override
        public Class<? extends Packet> packetClass() {
            return AuthResponsePacket.class;
        }
    }

    private class ClientGamePacketListener implements PacketListener {

        private final GameClient client;

        private ClientGamePacketListener(GameClient client) {
            this.client = client;
        }

        @Override
        public void handlePacket(Packet packet) {
//            log.info("Is null? " + Objects.isNull(packet));
            if(packet != null) {
                GamePacket gamePacket = (GamePacket) packet;
//                log.info("Test");
                client.enqueue(() -> {
                    blue.setLocalTranslation(gamePacket.getX(), blue.getLocalTranslation().getY(), blue.getLocalTranslation().getZ());
                    blue.setLocalRotation(
                            new Quaternion(
                                    gamePacket.getxAngle(),
                                    gamePacket.getyAngle(),
                                    gamePacket.getzAngle(),
                                    gamePacket.getW()));
                    return null;
                });
            }
        }

        @Override
        public Class<? extends Packet> packetClass() {
            return GamePacket.class;
        }
    }

	private void setLevel() {
		Spatial arena = assetManager.loadModel("arena.obj");
		arena.setLocalScale(0.6f);
		arena.setLocalTranslation(0, -20, 0);
		arena.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
		rootNode.attachChild(arena);
	}

	private void setLight() {
		DirectionalLight sun = new DirectionalLight();
		sun.setDirection(new Vector3f(1f, -1f, 1f).normalizeLocal());
		rootNode.addLight(sun);

		final int SHADOWMAP_SIZE=1024;
        DirectionalLightShadowRenderer dlsr = new DirectionalLightShadowRenderer(assetManager, SHADOWMAP_SIZE, 3);
        dlsr.setLight(sun);
        viewPort.addProcessor(dlsr);

        DirectionalLightShadowFilter dlsf = new DirectionalLightShadowFilter(assetManager, SHADOWMAP_SIZE, 3);
        dlsf.setLight(sun);
        dlsf.setEnabled(true);
        FilterPostProcessor fpp = new FilterPostProcessor(assetManager);
        fpp.addFilter(dlsf);
        viewPort.addProcessor(fpp);

		AmbientLight al = new AmbientLight();
		al.setColor(ColorRGBA.White.mult(1.3f));
		rootNode.addLight(al);
	}
}
