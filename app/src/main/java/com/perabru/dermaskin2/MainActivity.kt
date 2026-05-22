package com.perabru.dermaskin2

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.database.FirebaseDatabase

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    private lateinit var txtMode: TextView
    private lateinit var edtName: EditText
    private lateinit var edtEmail: EditText
    private lateinit var edtPassword: EditText
    private lateinit var checkTerms: CheckBox
    private lateinit var btnRegister: TextView
    private lateinit var btnLogin: TextView
    private lateinit var progressBar: ProgressBar

    private var isLoginMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()

        iniciarComponentes()
        configurarEstadoInicial()
        configurarCliques()

        val usuarioAtual = auth.currentUser
        if (usuarioAtual != null) {
            openAnaliseScreen()
        }
    }

    private fun iniciarComponentes() {
        txtMode = findViewById(R.id.txtMode)
        edtName = findViewById(R.id.edtName)
        edtEmail = findViewById(R.id.edtEmail)
        edtPassword = findViewById(R.id.edtPassword)
        checkTerms = findViewById(R.id.checkTerms)
        btnRegister = findViewById(R.id.btnRegister)
        btnLogin = findViewById(R.id.btnLogin)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun configurarEstadoInicial() {
        progressBar.visibility = View.GONE
        changeToRegisterMode()
    }

    private fun configurarCliques() {
        btnRegister.setOnClickListener {
            if (isLoginMode) {
                changeToRegisterMode()
            } else {
                registerUser()
            }
        }

        btnLogin.setOnClickListener {
            if (isLoginMode) {
                loginUser()
            } else {
                changeToLoginMode()
            }
        }
    }

    private fun changeToLoginMode() {
        isLoginMode = true
        limparErros()

        txtMode.text = "Entrar na conta"

        edtName.visibility = View.GONE
        checkTerms.visibility = View.GONE

        btnLogin.text = "Entrar"
        btnRegister.text = "Criar nova conta"

        edtName.text.clear()
        edtEmail.text.clear()
        edtPassword.text.clear()
        checkTerms.isChecked = false
    }

    private fun changeToRegisterMode() {
        isLoginMode = false
        limparErros()

        txtMode.text = "Criar conta"

        edtName.visibility = View.VISIBLE
        checkTerms.visibility = View.VISIBLE

        btnRegister.text = "Criar conta"
        btnLogin.text = "Já tenho conta"

        edtName.text.clear()
        edtEmail.text.clear()
        edtPassword.text.clear()
        checkTerms.isChecked = false
    }

    private fun registerUser() {
        val name = edtName.text.toString().trim()
        val email = edtEmail.text.toString().trim()
        val password = edtPassword.text.toString().trim()

        if (!validarCadastro(name, email, password)) {
            return
        }

        setLoading(true)

        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val userId = result.user?.uid

                if (userId.isNullOrEmpty()) {
                    setLoading(false)
                    Toast.makeText(
                        this,
                        "Erro ao obter ID do usuário.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@addOnSuccessListener
                }

                saveUserData(userId, name, email)
            }
            .addOnFailureListener { error ->
                setLoading(false)

                val message = when (error) {
                    is FirebaseAuthUserCollisionException ->
                        "Este e-mail já está cadastrado. Tente entrar na conta."

                    is FirebaseAuthInvalidCredentialsException ->
                        "E-mail inválido. Verifique e tente novamente."

                    else ->
                        "Erro ao criar conta: ${error.message}"
                }

                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
    }

    private fun validarCadastro(
        name: String,
        email: String,
        password: String
    ): Boolean {
        limparErros()

        when {
            name.isEmpty() -> {
                edtName.error = "Informe seu nome"
                edtName.requestFocus()
                return false
            }

            email.isEmpty() -> {
                edtEmail.error = "Informe seu e-mail"
                edtEmail.requestFocus()
                return false
            }

            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                edtEmail.error = "Informe um e-mail válido"
                edtEmail.requestFocus()
                return false
            }

            password.isEmpty() -> {
                edtPassword.error = "Informe sua senha"
                edtPassword.requestFocus()
                return false
            }

            password.length < 6 -> {
                edtPassword.error = "A senha precisa ter pelo menos 6 caracteres"
                edtPassword.requestFocus()
                return false
            }

            !checkTerms.isChecked -> {
                Toast.makeText(
                    this,
                    "Você precisa aceitar o aviso de triagem médica.",
                    Toast.LENGTH_LONG
                ).show()
                return false
            }
        }

        return true
    }

    private fun saveUserData(userId: String, name: String, email: String) {
        val database = FirebaseDatabase.getInstance().reference

        val userData = hashMapOf(
            "id" to userId,
            "nome" to name,
            "email" to email,
            "dataCadastro" to System.currentTimeMillis()
        )

        database.child("usuarios")
            .child(userId)
            .setValue(userData)
            .addOnSuccessListener {
                setLoading(false)

                Toast.makeText(
                    this,
                    "Conta criada com sucesso!",
                    Toast.LENGTH_LONG
                ).show()

                openAnaliseScreen()
            }
            .addOnFailureListener { error ->
                setLoading(false)

                Toast.makeText(
                    this,
                    "Conta criada, mas houve erro ao salvar os dados: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()

                openAnaliseScreen()
            }
    }

    private fun loginUser() {
        val email = edtEmail.text.toString().trim()
        val password = edtPassword.text.toString().trim()

        if (!validarLogin(email, password)) {
            return
        }

        setLoading(true)

        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                setLoading(false)

                Toast.makeText(
                    this,
                    "Login realizado com sucesso!",
                    Toast.LENGTH_LONG
                ).show()

                openAnaliseScreen()
            }
            .addOnFailureListener { error ->
                setLoading(false)

                val message = when (error) {
                    is FirebaseAuthInvalidUserException ->
                        "Usuário não encontrado. Crie uma conta primeiro."

                    is FirebaseAuthInvalidCredentialsException ->
                        "E-mail ou senha incorretos."

                    else ->
                        "Erro ao entrar: ${error.message}"
                }

                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
    }

    private fun validarLogin(email: String, password: String): Boolean {
        limparErros()

        when {
            email.isEmpty() -> {
                edtEmail.error = "Informe seu e-mail"
                edtEmail.requestFocus()
                return false
            }

            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                edtEmail.error = "Informe um e-mail válido"
                edtEmail.requestFocus()
                return false
            }

            password.isEmpty() -> {
                edtPassword.error = "Informe sua senha"
                edtPassword.requestFocus()
                return false
            }
        }

        return true
    }

    private fun openAnaliseScreen() {
        val intent = Intent(this, AnaliseActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun setLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE

        btnRegister.isEnabled = !isLoading
        btnLogin.isEnabled = !isLoading
        edtName.isEnabled = !isLoading
        edtEmail.isEnabled = !isLoading
        edtPassword.isEnabled = !isLoading
        checkTerms.isEnabled = !isLoading

        btnRegister.alpha = if (isLoading) 0.55f else 1.0f
        btnLogin.alpha = if (isLoading) 0.55f else 1.0f
    }

    private fun limparErros() {
        edtName.error = null
        edtEmail.error = null
        edtPassword.error = null
    }
}