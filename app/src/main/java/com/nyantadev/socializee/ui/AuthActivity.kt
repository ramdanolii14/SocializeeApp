package com.nyantadev.socializee.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.nyantadev.socializee.api.RetrofitClient
import com.nyantadev.socializee.databinding.ActivityAuthBinding
import com.nyantadev.socializee.repository.AppRepository
import com.nyantadev.socializee.utils.SessionManager
import com.nyantadev.socializee.viewmodel.AuthState
import com.nyantadev.socializee.viewmodel.AuthViewModel
import com.nyantadev.socializee.viewmodel.ViewModelFactory

class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding
    private lateinit var viewModel: AuthViewModel
    private lateinit var sessionManager: SessionManager
    private var isLoginMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)
        RetrofitClient.init(sessionManager)

        val repo = AppRepository(RetrofitClient.getApiService())
        viewModel = ViewModelProvider(this, ViewModelFactory(repo))[AuthViewModel::class.java]

        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        updateMode()

        binding.tvToggleMode.setOnClickListener {
            isLoginMode = !isLoginMode
            updateMode()
        }

        binding.btnSubmit.setOnClickListener {
            if (isLoginMode) performLogin() else performRegister()
        }
    }

    private fun updateMode() {
        if (isLoginMode) {
            binding.tvTitle.text = "Masuk"
            binding.tvSubtitle.text = "Selamat datang kembali!"
            binding.btnSubmit.text = "Masuk"
            binding.layoutDisplayName.visibility = View.GONE
            binding.layoutEmail.visibility = View.GONE
            binding.tvToggleMode.text = "Belum punya akun? Daftar"
        } else {
            binding.tvTitle.text = "Daftar"
            binding.tvSubtitle.text = "Buat akun baru"
            binding.btnSubmit.text = "Daftar"
            binding.layoutDisplayName.visibility = View.VISIBLE
            binding.layoutEmail.visibility = View.VISIBLE
            binding.tvToggleMode.text = "Sudah punya akun? Masuk"
        }
    }

    private fun performLogin() {
        val login = binding.etUsername.text.toString().trim()
        val password = binding.etPassword.text.toString()

        if (login.isEmpty() || password.isEmpty()) {
            showError("Isi semua kolom")
            return
        }
        viewModel.login(login, password)
    }

    private fun performRegister() {
        val username = binding.etUsername.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()
        val displayName = binding.etDisplayName.text.toString().trim()

        if (username.isEmpty() || email.isEmpty() || password.isEmpty() || displayName.isEmpty()) {
            showError("Isi semua kolom")
            return
        }
        if (password.length < 6) {
            showError("Password minimal 6 karakter")
            return
        }
        viewModel.register(username, email, password, displayName)
    }

    private fun observeViewModel() {
        viewModel.authState.observe(this) { state ->
            when (state) {
                is AuthState.Loading -> setLoading(true)
                is AuthState.Success -> {
                    setLoading(false)
                    sessionManager.saveToken(state.token)
                    sessionManager.saveUser(state.user)
                    goToMain()
                }
                is AuthState.Error -> {
                    setLoading(false)
                    showError(state.message)
                }
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSubmit.isEnabled = !loading
    }

    private fun showError(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
