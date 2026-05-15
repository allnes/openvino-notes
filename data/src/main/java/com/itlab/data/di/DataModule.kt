package com.itlab.data.di

import androidx.room.Room
import com.itlab.data.BuildConfig
import com.itlab.data.cloud.AuthManager
import com.itlab.data.db.AppDatabase
import com.itlab.data.mapper.NoteFolderMapper
import com.itlab.data.mapper.NoteMapper
import com.itlab.data.repository.AuthRepositoryImpl
import com.itlab.data.repository.NoteFolderRepositoryImpl
import com.itlab.data.repository.NotesRepositoryImpl
import com.itlab.domain.repository.AuthRepository
import com.itlab.domain.repository.NoteFolderRepository
import com.itlab.domain.repository.NotesRepository
import org.koin.dsl.module

val dataModule =
    module {
        single {
            com.google.firebase.auth.FirebaseAuth
                .getInstance()
        }

        single { AuthManager(get()) }

        single {
            Room
                .databaseBuilder(
                    get(),
                    AppDatabase::class.java,
                    "notes_database",
                ).apply {
                    if (BuildConfig.DEBUG) {
                        fallbackToDestructiveMigration(true)
                    }
                }.build()
        }

        single { get<AppDatabase>().noteDao() }
        single { get<AppDatabase>().mediaDao() }
        single { get<AppDatabase>().folderDao() }

        single { NoteMapper() }
        single { NoteFolderMapper() }

        single<NotesRepository> { NotesRepositoryImpl(get(), get(), get()) }
        single<NoteFolderRepository> { NoteFolderRepositoryImpl(get(), get()) }
        single<AuthRepository> { AuthRepositoryImpl(get()) }
    }
