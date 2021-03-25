package terrain;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.AmbientLight;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.PointLight;
import javafx.scene.Scene;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Mesh;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.transform.NonInvertibleTransformException;
import javafx.scene.transform.Rotate;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.fxyz3d.geometry.MathUtils;
import org.fxyz3d.io.OBJWriter;
import org.fxyz3d.scene.Axes;
import org.fxyz3d.utils.CameraTransformer;
import org.fxyz3d.utils.MeshUtils;

/**
 * @author Sean
 */
public class App extends Application {
    //**************************************************************************
    private final double sceneWidth = 4000;
    private final double sceneHeight = 4000;    
    
    private double mousePosX;
    private double mousePosY;
    private double mouseOldX;
    private double mouseOldY;
    private double mouseDeltaX;
    private double mouseDeltaY;
    private final double cameraDistance = -4000;
    
    private StackPane rootPane = new StackPane();
    private SubScene subScene;
    private final Group sceneRoot = new Group();
    private PerspectiveCamera camera;
    private final CameraTransformer cameraTransform = new CameraTransformer();
    public Color sceneColor = Color.BLACK;
    public Axes axes = new Axes(1);
    Color ALICEBLUETRANS = new Color(0.9411765f, 0.972549f, 1.0f, 0.25f);
    MeshView terrainMeshView;
    TriangleMesh terrainMesh;
    private ContextMenu cm;

    @Override
    public void start(Stage stage) throws NonInvertibleTransformException {
        createSubscene();        
        Scene scene = new Scene(rootPane, 1024, 768, true, SceneAntialiasing.BALANCED);          
        stage.setTitle("Terrain Demo");
        stage.setScene(scene);
        stage.setMaximized(true);
        stage.show();                        
    }
   
    private void createSubscene(){        
        subScene = new SubScene(sceneRoot, sceneWidth, sceneHeight, true, SceneAntialiasing.BALANCED);
        //Enable subScene resizing
        subScene.widthProperty().bind(rootPane.widthProperty());
        subScene.heightProperty().bind(rootPane.heightProperty());
        subScene.setFocusTraversable(true);
        subScene.setFill(sceneColor);
        
        //Setup Camera        
        camera = new PerspectiveCamera(true);
        cameraTransform.setTranslate(0, 0, 0);
        cameraTransform.getChildren().addAll(camera);
        camera.setNearClip(0.1);
        camera.setFarClip(100000.0);
        camera.setTranslateZ(cameraDistance);
        subScene.setCamera(camera);

        //add a Point Light for better viewing of the grid coordinate system
        PointLight light = new PointLight(Color.WHITE);
        cameraTransform.getChildren().add(light);
        light.setTranslateX(camera.getTranslateX());
        light.setTranslateY(camera.getTranslateY());
        light.setTranslateZ(camera.getTranslateZ());
        
        //3D Axes to help the viewer maintain perspective
        axes.setHeight(sceneWidth / 4);
        axes.setRadius(0.25);
        sceneRoot.getChildren().add(cameraTransform);
        sceneRoot.getChildren().addAll(axes);
                
        //All user controls setup here
        initFirstPersonControls(subScene);

        //Set up an easy drag and drop for loading a play back file
        subScene.setOnDragOver(event -> {
            Dragboard db = event.getDragboard();
            if (db.hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            } else {
                event.consume();
            }
        });
        
        // Dropping over surface
        subScene.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                success = true;
                String filePath = null;
                filePath = db.getFiles().get(0).getAbsolutePath();
                System.out.println(filePath);         
                if (db.hasFiles()) {
                    loadImageAsTerrain(db.getFiles().get(0));
                }             
            }
            event.setDropCompleted(success);
            event.consume();
        });   
        rootPane.getChildren().addAll(subScene);
        
        MenuItem cmObjExport = new MenuItem("Export to OBJ Model");
        cmObjExport.setOnAction(e -> {
            if(null != terrainMesh){
                FileChooser fc = new FileChooser();
                fc.setTitle("Browse to output file location...");
                fc.setSelectedExtensionFilter(new FileChooser.ExtensionFilter("OBJ 3D Model Files", "obj","OBJ"));
                File file = fc.showSaveDialog(null);
                if(null != file) {
                    OBJWriter objWriter = new OBJWriter(terrainMesh, file.getAbsolutePath());
                    objWriter.setTextureColors(1530);
                    objWriter.exportMesh();
                }   
            }
        });        
        MenuItem cmStlExport = new MenuItem("Export to STL Model");
        cmStlExport.setOnAction(e -> {
            if(null != terrainMesh){
                saveMeshToSTL(terrainMesh);
            }
        });        
        cm = new ContextMenu();
        cm.getItems().addAll(cmObjExport, cmStlExport);
        cm.setAutoFix(true);
        cm.setAutoHide(true);
        cm.setHideOnEscape(true);
        cm.setOpacity(0.66);
        subScene.setOnMouseClicked((MouseEvent e) -> {
            if (e.getButton() == MouseButton.SECONDARY) {
                if(null != cm)
                    if(!cm.isShowing()) 
                        cm.show(subScene.getParent(), e.getScreenX(), e.getScreenY());
                    else
                        cm.hide();
                e.consume();
            }
        });       
        initLights();
        
    }
    private void initLights(){
        
        PointLight headLight = new PointLight();        
        headLight.translateXProperty().bindBidirectional(camera.translateXProperty());
        headLight.translateYProperty().bindBidirectional(camera.translateYProperty());
        headLight.translateZProperty().bindBidirectional(camera.translateZProperty());
        headLight.setLightOn(true);
//        headLight.colorProperty().bind(headColorPicker.valueProperty());
        headLight.setRotationAxis(Rotate.Y_AXIS);
        headLight.setRotate(cameraTransform.getRotate());
        
        
        PointLight pointLight = new PointLight();
        pointLight.setTranslateX(-1000);
        pointLight.setTranslateY(-1000);
        pointLight.setTranslateZ(-1000);
        pointLight.setLightOn(true);
//        pointLight.colorProperty().bind(pointColorPicker.valueProperty());
        
        AmbientLight ambientLight = new AmbientLight();
        ambientLight.setTranslateY(-1000);
        ambientLight.setLightOn(false);
//        ambientLight.colorProperty().bind(ambientColorPicker.valueProperty());
        
        sceneRoot.getChildren().addAll(pointLight, headLight, ambientLight);
    }       
    private void initFirstPersonControls(SubScene scene){
        //make sure Subscene handles KeyEvents
        scene.setOnMouseEntered(e->{
            scene.requestFocus();
        });
        //First person shooter keyboard movement
        scene.setOnKeyPressed(event -> {
            double change = 10.0;
            //Add shift modifier to simulate "Running Speed"
            if(event.isShiftDown()) { change = 50.0; }
            //What key did the user press?
            KeyCode keycode = event.getCode();
            //Step 2c: Add Zoom controls
            if(keycode == KeyCode.W) { camera.setTranslateZ(camera.getTranslateZ() + change); }
            if(keycode == KeyCode.S) { camera.setTranslateZ(camera.getTranslateZ() - change); }
            //Step 2d: Add Strafe controls
            if(keycode == KeyCode.A) { camera.setTranslateX(camera.getTranslateX() - change); }
            if(keycode == KeyCode.D) { camera.setTranslateX(camera.getTranslateX() + change); }
//            //Add scaling buttons
//            if(keycode == KeyCode.I) { dataScaleProp.set(dataScaleProp.get() + 50.0); }
//            if(keycode == KeyCode.M) { dataScaleProp.set(dataScaleProp.get() - 50.0); }
        });
        
        scene.setOnMousePressed((MouseEvent me) -> {
            if(!scene.isFocused()){
                scene.requestFocus();
            }
            mousePosX = me.getSceneX();
            mousePosY = me.getSceneY();
            mouseOldX = me.getSceneX();
            mouseOldY = me.getSceneY();            
        });
        
        scene.setOnMouseDragged((MouseEvent me) -> {
            mouseOldX = mousePosX;
            mouseOldY = mousePosY;
            mousePosX = me.getSceneX();
            mousePosY = me.getSceneY();
            mouseDeltaX = (mousePosX - mouseOldX);
            mouseDeltaY = (mousePosY - mouseOldY);
            
            double modifier = 10.0;
            double modifierFactor = 0.1;
            
            if (me.isControlDown()) {
                modifier = 0.1;
            }
            if (me.isShiftDown()) {
                modifier = 50.0;
            }
            if (me.isPrimaryButtonDown()) {
                cameraTransform.ry.setAngle(((cameraTransform.ry.getAngle() + mouseDeltaX * modifierFactor * modifier * 2.0) % 360 + 540) % 360 - 180); // +
                cameraTransform.rx.setAngle(
                        MathUtils.clamp(-60, 
                        (((cameraTransform.rx.getAngle() - mouseDeltaY * modifierFactor * modifier * 2.0) % 360 + 540) % 360 - 180),
                        60)); // - 
                
            } else if (me.isSecondaryButtonDown()) {
                double z = camera.getTranslateZ();
                double newZ = z + mouseDeltaX * modifierFactor * modifier;
                camera.setTranslateZ(newZ);
            } else if (me.isMiddleButtonDown()) {
                cameraTransform.t.setX(cameraTransform.t.getX() + mouseDeltaX * modifierFactor * modifier * 0.3); // -
                cameraTransform.t.setY(cameraTransform.t.getY() + mouseDeltaY * modifierFactor * modifier * 0.3); // -
            }
        });
    }
    private WritableImage convertToGreyScale(Image image) {
        WritableImage tmp = new WritableImage(image.getPixelReader(),(int)image.getWidth(), (int)image.getHeight());
        for(int y = 0; y < (int)tmp.getHeight(); y++){
            for(int x = 0; x < (int)tmp.getWidth(); x++){
                tmp.getPixelWriter().setColor(x, y, image.getPixelReader().getColor(x, y).grayscale());
            }
        }                    
        return tmp;
    }
    private void loadImageAsTerrain(File file) {
        if (file != null) {
            Task <Void> task = new Task<Void>() {
                @Override
                protected Void call() throws Exception {
                    Platform.runLater(()-> sceneRoot.getChildren().remove(terrainMeshView));
                    Image meshImage = new Image(new FileInputStream(file));
                    Image greyscaleImage = convertToGreyScale(meshImage);
                    terrainMesh = createHeightMap(greyscaleImage, 10, 20, 1);
                    terrainMeshView = new MeshView(terrainMesh);
                    PhongMaterial material = new PhongMaterial();
                    material.setDiffuseMap(meshImage);
//                    material.setBumpMap(meshImage);
                    terrainMeshView.setMaterial(material);
                    Platform.runLater(() -> {         
                        sceneRoot.getChildren().add(terrainMeshView);
                    });
                    return null;
                }                
            };
            Thread thread = new Thread(task);
            thread.setDaemon(true);
            thread.start();
        }
    }    
    /**
     * Generically creates a TriangleMesh that represents the surface height map 
     * from an Image object. Will attempt to apply the Image itself as a texture 
     * to the TriangleMesh.
     *
     * @param image The Image object to convert to TriangleMesh
     * @param pskip
     * @param maxH
     * @param scale
     * @return TriangleMesh 3D surface height object generated from the Image.
     */      
    public static TriangleMesh createHeightMap(Image image, int pskip, float maxH, float scale) {
        float minX = -(float) image.getWidth() / 2;
        float maxX = (float) image.getWidth() / 2;
        float minY = -(float) image.getHeight() / 2;
        float maxY = (float) image.getHeight() / 2;

        if(pskip <= 0)
            pskip = 1;
        int subDivX = (int) image.getWidth() / pskip;
        int subDivY = (int) image.getHeight() / pskip;

        final int pointSize = 3;
        final int texCoordSize = 2;
        // 3 point indices and 3 texCoord indices per triangle
        final int faceSize = 6;
        int numDivX = subDivX + 1;
        int numVerts = (subDivY + 1) * numDivX;
        float points[] = new float[numVerts * pointSize];
        float texCoords[] = new float[numVerts * texCoordSize];
        int faceCount = subDivX * subDivY * 2;
        int faces[] = new int[faceCount * faceSize];

        // Create points and texCoords
        for (int y = 0; y < subDivY; y++) {
            float currY = (float) y / subDivY;
            double fy = (1 - currY) * minY + currY * maxY;
            for (int x = 0; x < subDivX; x++) {
                float currX = (float) x / subDivX;
                double fx = (1 - currX) * minX + currX * maxX;

                int index = y * numDivX * pointSize + (x * pointSize);
                points[index] = (float) fx * scale;   // x
                points[index + 1] = (float) fy * scale;  // y
                // color value for pixel at point
                int rgb = ((int) image.getPixelReader().getArgb(x * pskip, y * pskip)); 
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                points[index + 2] = -((float) ((r + g + b) / 3) / 255) * maxH; // z 

                index = y * numDivX * texCoordSize + (x * texCoordSize);
                texCoords[index] = currX;
                texCoords[index + 1] = currY;
            }
        }

        // Create faces
        for (int y = 0; y < subDivY; y++) {
            for (int x = 0; x < subDivX; x++) {
                int p00 = y * numDivX + x;
                int p01 = p00 + 1;
                int p10 = p00 + numDivX;
                int p11 = p10 + 1;
                int tc00 = y * numDivX + x;
                int tc01 = tc00 + 1;
                int tc10 = tc00 + numDivX;
                int tc11 = tc10 + 1;

                int index = (y * subDivX * faceSize + (x * faceSize)) * 2;
                faces[index + 0] = p00;
                faces[index + 1] = tc00;
                faces[index + 2] = p10;
                faces[index + 3] = tc10;
                faces[index + 4] = p11;
                faces[index + 5] = tc11;

                index += faceSize;
                faces[index + 0] = p11;
                faces[index + 1] = tc11;
                faces[index + 2] = p01;
                faces[index + 3] = tc01;
                faces[index + 4] = p00;
                faces[index + 5] = tc00;
            }
        }
        //@TODO
        //int smoothingGroups[] = new int[faces.length / faceSize];

        TriangleMesh mesh = new TriangleMesh();
        mesh.getPoints().addAll(points);
        mesh.getTexCoords().addAll(texCoords);
        mesh.getFaces().addAll(faces);
        //mesh.getFaceSmoothingGroups().addAll(smoothingGroups);
        return mesh;
    }    
    /**
     * Convenience method to save an arbitrary 3D Mesh object to a 3D STL model file.
     * Presents the user with a FileChooser dialog to browse the location and 
     * file name.
     * 
     * @param mesh The 3D Mesh object to save as an STL.
     */        
    public static void saveMeshToSTL(Mesh mesh) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Browse to output file location...");
        fc.setSelectedExtensionFilter(new FileChooser.ExtensionFilter("STL 3D Model Files", "stl","STL"));
        File file = fc.showSaveDialog(null);
        if(null != file) {
            try {
                MeshUtils.mesh2STL(file.getPath(), mesh);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }    
    
    /** Ye olde main()
     * @param args */    
    public static void main(String[] args) {
        launch(args);
    }
}