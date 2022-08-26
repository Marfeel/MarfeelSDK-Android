package com.marfeel.compass.core

import androidx.annotation.WorkerThread

interface UseCase<I, O> {

	@WorkerThread
	operator fun invoke(input: I): O
}

interface NoInputUseCase<O> {

	@WorkerThread
	operator fun invoke(): O
}
