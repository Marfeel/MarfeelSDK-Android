package com.marfeel.compass.core

import androidx.annotation.WorkerThread

internal interface UseCase<I, O> {

	@WorkerThread
	operator fun invoke(input: I): O
}

internal interface NoInputUseCase<O> {

	@WorkerThread
	operator fun invoke(): O
}
