package com.mikepenz.adbfriend

import OpenSourceInitiative
import adbfriend_root.adbfriend.generated.resources.Res
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.m3.LibraryDefaults
import org.jetbrains.compose.resources.ExperimentalResourceApi

@OptIn(ExperimentalMaterial3Api::class, ExperimentalResourceApi::class)
@Composable
fun App() {
    val uriHandler = LocalUriHandler.current
    var showLicenses by remember { mutableStateOf(false) }
    val animatedToolbarColor by animateColorAsState(
        if (showLicenses) Color.Unspecified else Color.Transparent
    )

    HypnoticCanvasTheme {
        Scaffold(
            topBar = {
                TopAppBar(title = {
                    Text("AdbFriend")
                }, modifier = Modifier.fillMaxWidth(), colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = animatedToolbarColor
                ), actions = {
                    IconButton(onClick = {
                        showLicenses = !showLicenses
                    }) {
                        Icon(
                            imageVector = OpenSourceInitiative, contentDescription = "Open Source"
                        )
                    }
                    IconButton(onClick = {
                        uriHandler.openUri("https://github.com/mikepenz/")
                    }) {
                        Icon(
                            imageVector = Github, contentDescription = "GitHub"
                        )
                    }
                })
            },
        ) { contentPadding ->
            Box(Modifier.fillMaxSize()) {
                if (showLicenses) {
                    var libs by remember { mutableStateOf<Libs?>(null) }
                    LaunchedEffect("aboutlibraries.json") {
                        libs = Libs.Builder().withJson(Res.readBytes("files/aboutlibraries.json").decodeToString()).build()
                    }
                    LibrariesContainer(
                        libraries = libs,
                        modifier = Modifier.fillMaxSize(),
                        colors = LibraryDefaults.libraryColors(backgroundColor = Color.Transparent),
                        contentPadding = contentPadding
                    )
                } else {

                }
            }
        }
    }
}
