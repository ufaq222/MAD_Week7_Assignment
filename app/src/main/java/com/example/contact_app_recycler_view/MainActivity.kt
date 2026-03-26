package com.example.contact_app_recycler_view

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class MainActivity : AppCompatActivity(), ContactAdapter.OnContactActionListener {
    private lateinit var etName: EditText
    private lateinit var etPhone: EditText
    private lateinit var etSearch: EditText
    private lateinit var btnSave: Button
    private lateinit var btnLoadContacts: Button
    private lateinit var btnSort: ImageButton
    private lateinit var ivAddProfile: ImageView
    private lateinit var recyclerViewContacts: RecyclerView

    private lateinit var contactAdapter: ContactAdapter
    private val contactList = mutableListOf<Contact>()
    private var filteredList = mutableListOf<Contact>()
    private var isAscending = true
    
    private var selectedImageUri: Uri? = null
    private var editingContactImageUri: Uri? = null
    private var ivEditingProfile: ImageView? = null

    // Photo picker launcher
    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            if (ivEditingProfile != null) {
                editingContactImageUri = it
                ivEditingProfile?.setImageURI(it)
            } else {
                selectedImageUri = it
                ivAddProfile.setImageURI(it)
            }
        }
    }

    // for contact loading permission request
    private val requestContactsPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                loadContactsFromPhone()
            } else {
                Toast.makeText(this, "Contacts permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Initialize UI components
        etName = findViewById(R.id.etName)
        etPhone = findViewById(R.id.etPhone)
        etSearch = findViewById(R.id.etSearch)
        btnSave = findViewById(R.id.btnSave)
        btnLoadContacts = findViewById(R.id.btnLoadContacts)
        btnSort = findViewById(R.id.btnSort)
        ivAddProfile = findViewById(R.id.ivAddProfile)
        recyclerViewContacts = findViewById(R.id.recyclerViewContacts)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Setup RecyclerView
        filteredList.addAll(contactList)
        contactAdapter = ContactAdapter(filteredList, this)
        recyclerViewContacts.layoutManager = LinearLayoutManager(this)
        recyclerViewContacts.adapter = contactAdapter

        // Photo Picker for NEW contact
        ivAddProfile.setOnClickListener {
            ivEditingProfile = null // Ensure we know we are NOT editing
            pickImage.launch("image/*")
        }

        // Button Click Listeners
        btnSave.setOnClickListener {
            saveContact()
        }

        btnLoadContacts.setOnClickListener {
            checkPermissionAndLoadContacts()
        }

        btnSort.setOnClickListener {
            sortContacts()
        }

        // Search Listener - Now searches by Name OR Phone
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filter(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun filter(text: String) {
        val query = text.lowercase(Locale.ROOT)
        val filtered = if (query.isEmpty()) {
            contactList
        } else {
            contactList.filter {
                it.name.lowercase(Locale.ROOT).contains(query) ||
                it.phone.contains(query)
            }
        }
        filteredList.clear()
        filteredList.addAll(filtered)
        contactAdapter.notifyDataSetChanged()
    }

    private fun sortContacts() {
        if (isAscending) {
            contactList.sortByDescending { it.name.lowercase(Locale.ROOT) }
            btnSort.setImageResource(android.R.drawable.ic_menu_sort_by_size)
        } else {
            contactList.sortBy { it.name.lowercase(Locale.ROOT) }
            btnSort.setImageResource(android.R.drawable.ic_menu_sort_alphabetically)
        }
        isAscending = !isAscending
        filter(etSearch.text.toString())
    }

    private fun saveContact() {
        val name = etName.text.toString().trim()
        val phone = etPhone.text.toString().trim()

        if (!validateInputs(name, phone, etName, etPhone)) {
            return
        }

        val newContact = Contact(name, phone, selectedImageUri?.toString())
        contactList.add(newContact)
        filter(etSearch.text.toString())

        Toast.makeText(this, "Contact saved successfully", Toast.LENGTH_SHORT).show()

        // Reset UI for next contact
        etName.text.clear()
        etPhone.text.clear()
        ivAddProfile.setImageResource(android.R.drawable.ic_menu_add)
        selectedImageUri = null
        etName.requestFocus()
    }

    private fun validateInputs(name: String, phone: String, nameInput: EditText, phoneInput: EditText): Boolean {
        var isValid = true

        if (name.isEmpty()) {
            nameInput.error = "Name is required"
            isValid = false
        }

        if (phone.isEmpty()) {
            phoneInput.error = "Phone number is required"
            isValid = false
        } else if (phone.length < 10 || !phone.all { it.isDigit() || it == '+' }) {
            phoneInput.error = "Enter valid phone number"
            isValid = false
        }

        return isValid
    }

    override fun onItemClick(position: Int) {
        val contact = filteredList[position]
        Toast.makeText(this, "Contact: ${contact.name}\nPhone: ${contact.phone}", Toast.LENGTH_SHORT).show()
    }

    override fun onEditClick(position: Int) {
        showEditDialog(position)
    }

    override fun onDeleteClick(position: Int) {
        showDeleteDialog(position)
    }

    private fun showDeleteDialog(position: Int) {
        AlertDialog.Builder(this)
            .setTitle("Delete Contact")
            .setMessage("Are you sure you want to delete this contact?")
            .setPositiveButton("Yes") { _, _ ->
                val contactToRemove = filteredList[position]
                contactList.remove(contactToRemove)
                filter(etSearch.text.toString())
                Toast.makeText(this, "Contact deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun checkPermissionAndLoadContacts() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED -> {
                loadContactsFromPhone()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS) -> {
                AlertDialog.Builder(this)
                    .setTitle("Permission Required")
                    .setMessage("This app needs permission to read your contacts to display them.")
                    .setPositiveButton("Grant") { _, _ ->
                        requestContactsPermission.launch(Manifest.permission.READ_CONTACTS)
                    }
                    .setNegativeButton("Deny", null)
                    .show()
            }
            else -> {
                requestContactsPermission.launch(Manifest.permission.READ_CONTACTS)
            }
        }
    }

    private fun loadContactsFromPhone() {
        val loadedContacts = mutableListOf<Contact>()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI
        )

        val cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )

        cursor?.use {
            val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val phoneIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val photoIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)

            while (it.moveToNext()) {
                val name = it.getString(nameIndex) ?: ""
                val phone = it.getString(phoneIndex) ?: ""
                val photoUri = it.getString(photoIndex)

                if (name.isNotBlank() && phone.isNotBlank()) {
                    loadedContacts.add(Contact(name, phone, photoUri))
                }
            }
        }

        if (loadedContacts.isEmpty()) {
            Toast.makeText(this, "No contacts found on your phone", Toast.LENGTH_SHORT).show()
            return
        }

        contactList.clear()
        contactList.addAll(loadedContacts)
        filter(etSearch.text.toString())

        Toast.makeText(this, "${loadedContacts.size} contacts loaded", Toast.LENGTH_SHORT).show()
    }

    private fun showEditDialog(position: Int) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.activity_dialog_edit_item, null)
        val etEditName = dialogView.findViewById<EditText>(R.id.etEditName)
        val etEditPhone = dialogView.findViewById<EditText>(R.id.etEditPhone)
        val ivEditProfile = dialogView.findViewById<ImageView>(R.id.ivEditProfile)

        val contact = filteredList[position]
        etEditName.setText(contact.name)
        etEditPhone.setText(contact.phone)
        
        // Show current photo
        if (contact.photoUri != null) {
            ivEditProfile.setImageURI(Uri.parse(contact.photoUri))
            editingContactImageUri = Uri.parse(contact.photoUri)
        } else {
            ivEditProfile.setImageResource(android.R.drawable.ic_menu_add)
            editingContactImageUri = null
        }

        // Handle Photo picking in Edit Dialog
        ivEditProfile.setOnClickListener {
            ivEditingProfile = ivEditProfile // Target the dialog's ImageView
            pickImage.launch("image/*")
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Edit Contact")
            .setView(dialogView)
            .setPositiveButton("Update", null)
            .setNegativeButton("Cancel") { _, _ ->
                ivEditingProfile = null // Clear reference when dialog closed
            }
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val updatedName = etEditName.text.toString().trim()
            val updatedPhone = etEditPhone.text.toString().trim()

            if (validateInputs(updatedName, updatedPhone, etEditName, etEditPhone)) {
                contact.name = updatedName
                contact.phone = updatedPhone
                contact.photoUri = editingContactImageUri?.toString()
                
                contactAdapter.notifyItemChanged(position)
                Toast.makeText(this, "Contact updated", Toast.LENGTH_SHORT).show()
                ivEditingProfile = null // Clear reference
                dialog.dismiss()
            }
        }
    }
}
