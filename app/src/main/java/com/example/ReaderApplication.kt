package com.example

import android.app.Application
import androidx.room.Room
import com.example.data.db.AppDatabase
import com.example.data.repository.StudyRepositoryImpl
import com.example.domain.repository.StudyRepository

class ReaderApplication : Application() {
    lateinit var database: AppDatabase
    lateinit var repository: StudyRepository
    lateinit var epubExtractor: com.example.domain.repository.EpubExtractor
    lateinit var importBookUseCase: com.example.domain.usecase.ImportBookUseCase

    override fun onCreate() {
        super.onCreate()
        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "sq5r_reader_db"
        ).fallbackToDestructiveMigration().build()
        
        repository = StudyRepositoryImpl(database)
        epubExtractor = com.example.data.repository.EpubExtractorImpl()
        importBookUseCase = com.example.domain.usecase.ImportBookUseCase(repository, epubExtractor)
    }
}
