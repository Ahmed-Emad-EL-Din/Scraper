plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
}

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import java.io.File

// Deterministic image rasterization to exactly produce the attached icon representation
fun generatePrerenderedIcons() {
  println("--- GENERATING HIGH-QUALITY RASTER LAUNCHER ICONS ---")
  
  // Set headless AWT mode to function perfectly in CLI server environments
  System.setProperty("java.awt.headless", "true")
  
  val fileToCheck = File("/app/applet/appicon.jpg")
  println("CHECKING USER IMAGE: ${fileToCheck.absolutePath} exists: ${fileToCheck.exists()} size: ${fileToCheck.length()} bytes")

  val resDirs = listOf(
    File(projectDir, "src/main/res"),
    File("/app/src/main/res"),
    File("/app/applet/app/src/main/res")
  )
  
  var resDir: File? = null
  for (d in resDirs) {
    if (d.exists() && File(d, "mipmap-hdpi").exists()) {
      resDir = d
      break
    }
  }
  
  if (resDir == null) {
    println("WARNING: res directory not found, skipping icon rasterization!")
    return
  }
  
  println("Using resource directory: ${resDir.absolutePath}")
  
  val mips = mapOf(
    "mipmap-mdpi" to 48,
    "mipmap-hdpi" to 72,
    "mipmap-xhdpi" to 96,
    "mipmap-xxhdpi" to 144,
    "mipmap-xxxhdpi" to 192
  )
  
  for ((folder, size) in mips) {
    val destFolder = File(resDir, folder)
    if (!destFolder.exists()) {
      destFolder.mkdirs()
    }
    
    // Delete legacy .webp files to avoid conflicts/merging errors
    listOf("ic_launcher.webp", "ic_launcher_round.webp").forEach { fileName ->
      val oldFile = File(destFolder, fileName)
      if (oldFile.exists()) {
        val deleted = oldFile.delete()
        println("Deleted legacy file: ${oldFile.absolutePath} = $deleted")
      }
    }
    
    // Render square icon
    val squareImg = drawLauncherIcon(size, false)
    val squareDest = File(destFolder, "ic_launcher.png")
    ImageIO.write(squareImg, "png", squareDest)
    println("Rendered ${squareDest.absolutePath} at ${size}x${size}")
    
    // Render rounded icon
    val roundImg = drawLauncherIcon(size, true)
    val roundDest = File(destFolder, "ic_launcher_round.png")
    ImageIO.write(roundImg, "png", roundDest)
    println("Rendered ${roundDest.absolutePath} at ${size}x${size}")
  }

  // Render high-resolution cropped drawable icon for the adaptive foreground
  val drawableFolder = File(resDir, "drawable")
  if (!drawableFolder.exists()) {
    drawableFolder.mkdirs()
  }
  val cropped512 = drawLauncherIcon(512, false)
  val croppedDest = File(drawableFolder, "app_icon_cropped.png")
  ImageIO.write(cropped512, "png", croppedDest)
  println("Rendered high-res cropped icon at 512x512 to ${croppedDest.absolutePath}")
  
  println("--- FINISHED RASTER ICON GENERATION ---")
}

fun drawLauncherIcon(size: Int, isRound: Boolean): BufferedImage {
  val srcFile = File("/app/applet/appicon.jpg")
  if (srcFile.exists()) {
    try {
      val src = ImageIO.read(srcFile)
      if (src != null) {
        val w = src.width
        val h = src.height
        
        // 1. Get background color from 4 corners
        val corners = listOf(
          src.getRGB(0, 0),
          src.getRGB(w - 1, 0),
          src.getRGB(0, h - 1),
          src.getRGB(w - 1, h - 1)
        )
        
        // Helper to measure color distance
        fun getDiff(c1: Int, c2: Int): Double {
          val r1 = (c1 shr 16) and 0xFF
          val g1 = (c1 shr 8) and 0xFF
          val b1 = c1 and 0xFF
          val r2 = (c2 shr 16) and 0xFF
          val g2 = (c2 shr 8) and 0xFF
          val b2 = c2 and 0xFF
          val dr = r1 - r2
          val dg = g1 - g2
          val db = b1 - b2
          return Math.sqrt((dr * dr + dg * dg + db * db).toDouble())
        }
        
        val firstCorner = corners[0]
        val cornersSimilar = corners.all { getDiff(it, firstCorner) < 30.0 }
        
        var croppedImg = src
        if (cornersSimilar) {
          // Bounding box of non-background pixels
          var minX = w
          var minY = h
          var maxX = 0
          var maxY = 0
          
          for (y in 0 until h) {
            for (x in 0 until w) {
              val rgb = src.getRGB(x, y)
              if (getDiff(rgb, firstCorner) >= 30.0) {
                if (x < minX) minX = x
                if (y < minY) minY = y
                if (x > maxX) maxX = x
                if (y > maxY) maxY = y
              }
            }
          }
          
          if (minX < maxX && minY < maxY) {
            val cropW = maxX - minX + 1
            val cropH = maxY - minY + 1
            val contentSize = maxOf(cropW, cropH)
            
            // Add a small 5% padding so we don't crop exactly on the logo boundary
            val padding = (contentSize * 0.05).toInt().coerceAtLeast(4)
            val paddedSize = contentSize + padding * 2
            
            val centerX = minX + cropW / 2.0
            val centerY = minY + cropH / 2.0
            
            var finalX = (centerX - paddedSize / 2.0).toInt()
            var finalY = (centerY - paddedSize / 2.0).toInt()
            
            // Adjust bounds to stay inside native image dimensions
            if (finalX < 0) finalX = 0
            if (finalY < 0) finalY = 0
            
            var finalSize = paddedSize
            if (finalX + finalSize > w) finalSize = w - finalX
            if (finalY + finalSize > h) finalSize = h - finalY
            
            // Ensure strict 1:1 ratio
            finalSize = minOf(finalSize, w - finalX, h - finalY)
            
            if (finalSize > 10) {
              croppedImg = src.getSubimage(finalX, finalY, finalSize, finalSize)
              println("Auto-cropped image around central content. Size: ${finalSize}x${finalSize} from (${finalX}, ${finalY})")
            }
          }
        } else {
          // Fallback to center-square crop if corners are not uniform
          val minDim = minOf(w, h)
          val cx = w / 2
          val cy = h / 2
          val x = cx - minDim / 2
          val y = cy - minDim / 2
          croppedImg = src.getSubimage(x, y, minDim, minDim)
          println("Center-cropped non-square image to square of size: ${minDim}x${minDim}")
        }
        
        val out = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g2 = out.createGraphics()
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        
        if (size == 512) {
          // For high-res cropped drawable foreground, draw edge-to-edge
          g2.drawImage(croppedImg, 0, 0, size, size, null)
        } else {
          // Fill canvas background with solid white
          g2.color = Color.WHITE
          g2.fillRect(0, 0, size, size)
          
          if (isRound) {
            val clip = Ellipse2D.Float(0f, 0f, size.toFloat(), size.toFloat())
            g2.clip(clip)
          }
          
          // Render central squircle scaled down to 72% to fit perfectly inside boundaries
          val targetSize = (size * 0.72).toInt()
          val offset = (size - targetSize) / 2
          g2.drawImage(croppedImg, offset, offset, targetSize, targetSize, null)
        }
        
        g2.dispose()
        return out
      }
    } catch (e: Exception) {
      println("Error reading or cropping user image: ${e.message}. Falling back to blank canvas.")
    }
  }

  // Fallback blank blue canvas if no image exists
  val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
  val g = img.createGraphics()
  g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
  g.color = Color(0x53, 0x50, 0xec)
  g.fillRect(0, 0, size, size)
  g.dispose()
  return img
}

generatePrerenderedIcons()

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.twobinventor.webmonitor"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  // implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.work.runtime)
  implementation(libs.androidx.security.crypto)
  implementation(libs.jsoup)
  // implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
