package com.example.omireadersdk.sdk.di

import com.example.omireadersdk.sdk.parser.EpubParser
import com.example.omireadersdk.sdk.parser.SmilParser
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SdkModule {

    @Provides
    @Singleton
    fun provideEpubParser(): EpubParser = EpubParser()

    @Provides
    @Singleton
    fun provideSmilParser(): SmilParser = SmilParser()
}