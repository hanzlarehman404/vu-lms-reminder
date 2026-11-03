package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import com.example.R
import coil.compose.AsyncImage
import com.example.ui.components.PrivacyDisclaimerDialog
import com.example.ui.components.SecurityVerificationDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.LoginUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    loginState: LoginUiState,
    isPrivacyAccepted: Boolean,
    onAcceptPrivacy: () -> Unit,
    onLogin: (studentId: String, password: String) -> Unit,
    onResetLoginState: () -> Unit
) {
    var studentId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var showSecurityVerification by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current

    if (!isPrivacyAccepted) {
        PrivacyDisclaimerDialog(onAccept = onAcceptPrivacy)
    }

    if (showSecurityVerification) {
        SecurityVerificationDialog(
            onDismiss = { showSecurityVerification = false },
            onContinueSignIn = {
                showSecurityVerification = false
                onLogin(studentId, password)
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                // Official VU LMS Logo (16:10 aspect ratio) with bundled vector fallback
                AsyncImage(
                    model = "https://vulms.vu.edu.pk/App_Themes/Default/Images/logos/logo-1.png",
                    placeholder = painterResource(R.drawable.ic_vu_logo),
                    error = painterResource(R.drawable.ic_vu_logo),
                    fallback = painterResource(R.drawable.ic_vu_logo),
                    contentDescription = "Virtual University Logo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .aspectRatio(16f / 10f)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "VU LMS Reminder",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "Virtual University of Pakistan",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = VuSuccessMint.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, VuSuccessMint.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = "🔒 Direct Connection to vulms.vu.edu.pk",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = VuSuccessMint,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Login Form Card
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(
                            text = "Student Sign In",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Student ID Input with changed placeholder bc240417499
                        OutlinedTextField(
                            value = studentId,
                            onValueChange = {
                                studentId = it
                                onResetLoginState()
                            },
                            label = { Text("VU Student ID") },
                            placeholder = { Text("e.g. bc240417499") },
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null, tint = VuInfoCyan)
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Next
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("student_id_input")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Password Input
                        OutlinedTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                onResetLoginState()
                            },
                            label = { Text("LMS Password") },
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = VuInfoCyan)
                            },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                    )
                                }
                            },
                            singleLine = true,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus()
                                    when {
                                        studentId.isBlank() -> {
                                            validationError = "Please enter your VU Student ID (e.g. bc240417499)"
                                        }
                                        password.isBlank() -> {
                                            validationError = "Please enter your LMS password"
                                        }
                                        else -> {
                                            validationError = null
                                            showSecurityVerification = true
                                        }
                                    }
                                }
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("password_input")
                        )

                        // Error message
                        if (loginState is LoginUiState.Error) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Text(
                                    text = loginState.message,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        // Loading state
                        if (loginState is LoginUiState.Loading) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = VuInfoCyan
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = loginState.step,
                                    fontSize = 12.sp,
                                    color = VuInfoCyan,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        if (validationError != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Text(
                                    text = validationError!!,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                when {
                                    studentId.isBlank() -> {
                                        validationError = "Please enter your VU Student ID (e.g. bc240417499)"
                                    }
                                    password.isBlank() -> {
                                        validationError = "Please enter your LMS password"
                                    }
                                    else -> {
                                        validationError = null
                                        showSecurityVerification = true
                                    }
                                }
                            },
                            enabled = loginState !is LoginUiState.Loading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("login_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = VuInfoCyan)
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Verify & Sign In",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Sessions are authenticated directly against https://vulms.vu.edu.pk via ASP.NET WebForms.",
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
