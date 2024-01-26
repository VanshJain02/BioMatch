from sklearn.cluster import KMeans, MiniBatchKMeans
import numpy as np
import cv2
from PIL import Image
import base64
import io
import scipy
from scipy import signal
from scipy import ndimage
import math
from skimage.util import view_as_blocks

import skimage.morphology
from skimage.morphology import convex_hull_image, erosion
from skimage.morphology import square


import fingerprint_enhancer
from scipy.fft import fft, fftfreq


def main(data,mod):
	decodedData = base64.b64decode(data)
	npData = np.fromstring(decodedData,np.uint8)
	imge = cv2.imdecode(npData,cv2.IMREAD_UNCHANGED)

	# kernel2 = np.array([[-1,-1,-1], [-1,9,-1], [-1,-1,-1]])

	# image1 = cv2.filter2D(src= imge, ddepth=-1, kernel=kernel2)

	# image = cv2.pyrUp(image1)


	# image = sharp_mask(image)

# image = imge.copy()

	# imge= cv2.flip(imge,1)

	print("Hello")

	masked_image = return_masked_image(imge,0.1)
	masked_image = masked_image.astype(np.uint8)

	if(mod==2):
		masked_1 = cv2.cvtColor(masked_image, cv2.COLOR_BGR2GRAY)
		thresholded_image1 = cv2.adaptiveThreshold(masked_1, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C, cv2.THRESH_BINARY, 11, 1)
		final_image = cv2.cvtColor(thresholded_image1, cv2.COLOR_BGR2RGB)

	elif(mod==3):

		image_enhancer = FingerprintImageEnhancer()
		thresholded_image1 = apply_adaptive_mean_thresholding(masked_image,"GAUSSIAN",15,1) #11 for old siamese +scat model

		th_img = (thresholded_image1)*255
		print("ATM DONE")

		kernel = np.array([[0,0, 0],
						   [0, 9,0],
						   [0, 0, 0]])
		thresholded_image1 = cv2.filter2D(src=th_img, ddepth=-1, kernel=kernel)
		print("SHARPEN DONE")

		try:
			# final_image = thresholded_image1.astype(np.uint8)
			final_image = image_enhancer.enhance(thresholded_image1.astype(np.uint8))
			print("ENHANCE DONE")

	# final_image = thresholded_image1.astype(np.uint8)
		except:
			print("ERROR!!!NO PROBLEM")
			final_image = thresholded_image1.astype(np.uint8)
	elif(mod==4):
		masked_1 = cv2.cvtColor(masked_image, cv2.COLOR_BGR2GRAY)
		kernel2 = np.array([[-1,-1,-1], [-1,9,-1], [-1,-1,-1]])
		sobelx1 = cv2.Sobel(masked_1,cv2.CV_8U,1,0,ksize=5)  # x
		sobely1 = cv2.Sobel(masked_1,cv2.CV_8U,0,1,ksize=5)  # y
		sobelxy1 = cv2.addWeighted(sobelx1, 0.5, sobely1, 0.5,0)
		sobelxy1 = cv2.filter2D(src= sobelxy1, ddepth=-1, kernel=kernel2)
		thresholded_image = cv2.adaptiveThreshold(sobelxy1, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C,cv2.THRESH_BINARY, 15,1)
		image_enhancer = FingerprintImageEnhancer()
		final_image = image_enhancer.enhance(thresholded_image.astype(np.uint8))

	elif(mod==6):
		image_enhancer = FingerprintImageEnhancer()
		thresholded_image1 = apply_adaptive_mean_thresholding(masked_image,"GAUSSIAN",15,1)
		final_image = image_enhancer.enhance(thresholded_image1.astype(np.uint8))
		out1 = fingerprint_enhancer.enhance_Fingerprint(final_image)
		out1 = cv2.resize(out1, (500,1000))
		final_image = extract_minutiae_features(out1,10,False,True, True )

	# 	NEW INTERNS PREPROCESSING
	elif(mod==10):
		image_enhancer = FingerprintImageEnhancerNew()

		x, mask = return_masked_image_new(imge, 0.1)

		mask = mask.astype(np.uint8)
		th_image = apply_adaptive_mean_thresholding_new(mask, "GAUSSIAN", 15, 1)
		th_img = th_image * 255

		static_kernel = np.array([[0, 0, 0], [0, 9, 0], [0, 0, 0]])
		th_img = cv2.filter2D(th_img, -1, static_kernel)
		th_image = (th_img > 127).astype(np.uint8) * 255
		print("Starting Gabor Filter")
		try:
			enhanced_image = image_enhancer.enhance(255 - th_img)
		except Exception as e:
			print("Enhancement error:", e)
			enhanced_image = imge
		print("Gabor Filter Ended")
		final_image= enhanced_image



	else:
		masked_1 = cv2.cvtColor(masked_image, cv2.COLOR_BGR2GRAY)
		kernel2 = np.array([[-1,-1,-1], [-1,9,-1], [-1,-1,-1]])
		sobelx1 = cv2.Sobel(masked_1,cv2.CV_8U,1,0,ksize=5)  # x
		sobely1 = cv2.Sobel(masked_1,cv2.CV_8U,0,1,ksize=5)  # y
		sobelxy1 = cv2.addWeighted(sobelx1, 0.5, sobely1, 0.5,0)
		sobelxy1 = cv2.filter2D(src= sobelxy1, ddepth=-1, kernel=kernel2)
		final_image = sobelxy1


	# masked_image = masked_image.astype(np.uint8)
	# masked_1 = cv2.flip(masked_image, 1)
	# masked_1 = masked_image.copy()


	# image_enhancer = FingerprintImageEnhancer()
	#
	# thresholded_image1 = apply_adaptive_mean_thresholding(masked_1,"GAUSSIAN",11,1)
	#
	# final_image = image_enhancer.enhance(thresholded_image1.astype(np.uint8))


	# thresholded_image1 = cv2.flip(thresholded_image1, 1)
	# pil_im = Image.fromarray(cv2.cvtColor(enhanced_image1, cv2.COLOR_BGR2RGB))

	pil_im = Image.fromarray(final_image)
	buff = io.BytesIO()
	pil_im.save(buff,format="PNG")
	img_str = base64.b64encode(buff.getvalue())
	return ""+str(img_str,'utf-8')

def getpixel(data):
	decodedData = base64.b64decode(data)
	npData = np.fromstring(decodedData,np.uint8)
	imge = cv2.imdecode(npData,cv2.IMREAD_GRAYSCALE)
	anc_img = cv2.resize(imge, (200, 200))
	ans=""

	# img1 =(np.float32(np.expand_dims(anc_img, 0)))
	le=0
	for i in range(200):
		for j in range(200):
			# for k in range(3):
				le+=1
				if(i==199 and j==199):
					ans+=str(anc_img[i][j])
				else:
					ans+=str(anc_img[i][j])+" "

			# print(f"{str(anc_img[i][j][0])}\t{str(anc_img[i][j][1])}\t{str(anc_img[i][j][2])}")

		# print("\n\n")
	# print(le)

	return ans

class FingerprintImageEnhancerNew(object):
	def __init__(self):
		self.ridge_segment_blksze = 32
		self.ridge_segment_thresh = 0.1
		self.gradient_sigma = 1
		self.block_sigma = 7
		self.orient_smooth_sigma = 7
		self.ridge_freq_blksze = 38
		self.ridge_freq_windsze = 5
		self.min_wave_length = 5
		self.max_wave_length = 15
		self.kx = 1.0
		self.ky = 1.0
		self.angleInc = 3
		self.ridge_filter_thresh = -1


		self._mask = []
		self._normim = []
		self._orientim = []
		self._mean_freq = []
		self._median_freq = []
		self._freq = []
		self._freqim = []
		self._binim = []

	def __normalise(self, img, mean, std):
		if(np.std(img) == 0):
			raise ValueError("Image standard deviation is 0. Please review image again")
		normed = (img - np.mean(img)) / (np.std(img))
		return (normed)

	# using view_as_blocks and np.pad and np.kron instead of nested loops
	def __ridge_segment(self, img):
		rows, cols = img.shape
		im = self.__normalise(img, 0, 1)  # normalising the image

		# Calculate the number of blocks needed
		new_rows = int(self.ridge_segment_blksze * np.ceil(float(rows) / self.ridge_segment_blksze))
		new_cols = int(self.ridge_segment_blksze * np.ceil(float(cols) / self.ridge_segment_blksze))

		# Efficient padding
		padded_img = np.pad(im, ((0, new_rows - rows), (0, new_cols - cols)), 'constant')

		# Efficient block processing
		block_shape = (self.ridge_segment_blksze, self.ridge_segment_blksze)
		blocks = view_as_blocks(padded_img, block_shape)

		# Vectorized standard deviation calculation
		stddev_blocks = np.std(blocks, axis=(2, 3))
		stddevim = np.kron(stddev_blocks, np.ones(block_shape))

		# Trimming to the original size and creating a mask
		stddevim = stddevim[:rows, :cols]
		self._mask = stddevim > self.ridge_segment_thresh

		# Normalizing using the mask
		mean_val = np.mean(im[self._mask])
		std_val = np.std(im[self._mask])
		self._normim = (im - mean_val) / std_val

	# using ** instead of np.power and reduced redundancies
	def __ridge_orient(self):
		rows, cols = self._normim.shape

		sze = np.fix(6 * self.gradient_sigma)
		sze = sze + 1 if np.remainder(sze, 2) == 0 else sze

		gauss = cv2.getGaussianKernel(int(sze), self.gradient_sigma)
		f = np.dot(gauss, gauss.T)

		fy, fx = np.gradient(f)  # Gradient of Gaussian

		Gx = signal.convolve2d(self._normim, fx, mode='same')
		Gy = signal.convolve2d(self._normim, fy, mode='same')

		Gxx = Gx**2
		Gyy = Gy**2
		Gxy = 2 * Gx * Gy

		sze = np.fix(6 * self.block_sigma)
		gauss = cv2.getGaussianKernel(int(sze), self.block_sigma)
		f = np.dot(gauss, gauss.T)

		Gxx = ndimage.convolve(Gxx, f)
		Gyy = ndimage.convolve(Gyy, f)
		Gxy = ndimage.convolve(Gxy, f)

		denom = np.sqrt(Gxy**2 + (Gxx - Gyy)**2) + np.finfo(float).eps
		sin2theta = Gxy / denom
		cos2theta = (Gxx - Gyy) / denom

		# Smooth the orientations if needed
		if self.orient_smooth_sigma:
			sze = np.fix(6 * self.orient_smooth_sigma)
			sze = sze + 1 if np.remainder(sze, 2) == 0 else sze
			gauss = cv2.getGaussianKernel(int(sze), self.orient_smooth_sigma)
			f = np.dot(gauss, gauss.T)
			cos2theta = ndimage.convolve(cos2theta, f)
			sin2theta = ndimage.convolve(sin2theta, f)

		self._orientim = np.pi / 2 + np.arctan2(sin2theta, cos2theta) / 2

	# Efficient computation of mean and median frequency
	def __ridge_freq(self):
		rows, cols = self._normim.shape
		freq = np.zeros((rows, cols))

		# Process each block of the image
		for r in range(0, rows - self.ridge_freq_blksze, self.ridge_freq_blksze):
			for c in range(0, cols - self.ridge_freq_blksze, self.ridge_freq_blksze):
				blkim = self._normim[r:r + self.ridge_freq_blksze, c:c + self.ridge_freq_blksze]
				blkor = self._orientim[r:r + self.ridge_freq_blksze, c:c + self.ridge_freq_blksze]

				freq[r:r + self.ridge_freq_blksze, c:c + self.ridge_freq_blksze] = self.__frequest(blkim, blkor)

		freq = freq * self._mask
		non_zero_freq = freq[freq > 0]
		self._mean_freq = np.mean(non_zero_freq)
		self._median_freq = np.median(non_zero_freq)

		self._freq = self._mean_freq * self._mask

	# used np.where and removed unnecessary shape calculation
	def __frequest(self, blkim, blkor):
		rows, cols = blkim.shape
		cosorient = np.mean(np.cos(2 * blkor))
		sinorient = np.mean(np.sin(2 * blkor))
		orient = math.atan2(sinorient, cosorient) / 2

		rotim = scipy.ndimage.rotate(blkim, orient / np.pi * 180 + 90, axes=(1, 0), reshape=False, order=3, mode='nearest')

		cropsze = int(np.fix(rows / np.sqrt(2)))
		offset = int(np.fix((rows - cropsze) / 2))
		rotim = rotim[offset:offset + cropsze, offset:offset + cropsze]

		proj = np.sum(rotim, axis=0)
		dilation = scipy.ndimage.grey_dilation(proj, size=self.ridge_freq_windsze, structure=np.ones(self.ridge_freq_windsze))
		temp = np.abs(dilation - proj)
		peak_thresh = 2
		maxpts = (temp < peak_thresh) & (proj > np.mean(proj))
		maxind = np.where(maxpts)[0]

		if maxind.size < 2:
			return np.zeros(blkim.shape)
		else:
			NoOfPeaks = maxind.size
			waveLength = (maxind[-1] - maxind[0]) / (NoOfPeaks - 1)
			if self.min_wave_length <= waveLength <= self.max_wave_length:
				return 1 / np.double(waveLength) * np.ones(blkim.shape)
			else:
				return np.zeros(blkim.shape)

	# using vectorized operations and pre-compute orientations of gabor filter
	# adjusted the loop to only iterate over the valid part
	def __ridge_filter(self):
		im = np.double(self._normim)
		rows, cols = im.shape
		newim = np.zeros((rows, cols))

		freq_1d = self._freq[self._freq > 0]
		non_zero_elems_in_freq = np.round(freq_1d * 100) / 100
		unfreq = np.unique(non_zero_elems_in_freq)

		sigmax = 1 / unfreq[0] * self.kx
		sigmay = 1 / unfreq[0] * self.ky
		sze = int(np.round(3 * max(sigmax, sigmay)))
		x, y = np.meshgrid(np.linspace(-sze, sze, (2 * sze + 1)), np.linspace(-sze, sze, (2 * sze + 1)))
		reffilter = np.exp(-((x ** 2 / sigmax ** 2) + (y ** 2 / sigmay ** 2))) * np.cos(2 * np.pi * unfreq[0] * x)

		angleRange = int(180 / self.angleInc)
		gabor_filters = [scipy.ndimage.rotate(reffilter, -(o * self.angleInc + 90), reshape=False)
						 for o in range(angleRange)]

		# efficient filtering
		maxorientindex = round(180 / self.angleInc)
		orientindex = np.round(self._orientim / np.pi * 180 / self.angleInc) % maxorientindex

		valid_mask = (self._freq > 0) & (sze < rows - np.arange(rows)[:, None]) & (sze < np.arange(rows)[:, None]) & \
					 (sze < cols - np.arange(cols)) & (sze < np.arange(cols))

		for r in range(sze, rows - sze):
			for c in range(sze, cols - sze):
				if valid_mask[r, c]:
					img_block = im[r - sze:r + sze + 1, c - sze:c + sze + 1]
					filter_index = int(orientindex[r, c]) - 1
					newim[r, c] = np.sum(img_block * gabor_filters[filter_index])

		self._binim = newim < self.ridge_filter_thresh

	def save_enhanced_image(self, path):
		# saves the enhanced image at the specified path
		cv2.imwrite(path, (255 * self._binim))

	def enhance(self, img, resize=True):
		# main function to enhance the image.
		# calls all other subroutines

		if(resize):
			rows, cols = np.shape(img)
			aspect_ratio = np.double(rows) / np.double(cols)

			new_rows = 450                 # randomly selected number
			new_cols = new_rows / aspect_ratio

			img = cv2.resize(img, (int(new_cols), int(new_rows)))

		self.__ridge_segment(img)   # normalise the image and find a ROI
		self.__ridge_orient()       # compute orientation image
		self.__ridge_freq()         # compute major frequency of ridges
		self.__ridge_filter()       # filter the image using oriented gabor filter
		return(self._binim)

def apply_adaptive_mean_thresholding_new(image,method="GAUSSIAN",block_size=7,subtraction_const=1):
	"""NOTE: block size must be an odd number"""

	image_gray = cv2.cvtColor((image*255).astype(np.uint8), cv2.COLOR_BGR2GRAY)
	if method == "GAUSSIAN":
		thresholded_image = cv2.adaptiveThreshold(image_gray, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
												  cv2.THRESH_BINARY, block_size, subtraction_const)

	elif method == "MEAN":
		thresholded_image = cv2.adaptiveThreshold(image_gray, 255, cv2.ADAPTIVE_THRESH_MEAN_C,
												  cv2.THRESH_BINARY, block_size, subtraction_const)

	else: print("Wrong method name used!"); return;

	return thresholded_image.astype(np.float64)/255


def return_masked_image_new(image, spatial_weight):
	# print(image)
	dim1, dim2 = image.shape[:2]
	image_convert = cv2.cvtColor(image, cv2.COLOR_BGR2YCR_CB)

	xb, xa = np.meshgrid(np.linspace(1, dim2, dim2), np.linspace(1, dim1, dim1))

	image_convert_concat = np.concatenate((image_convert, xb[..., np.newaxis], xa[..., np.newaxis]), axis=2)

	image_convert_reshape = np.reshape(image_convert_concat, (dim1 * dim2, 5))
	image_convert_reshape_mean = np.mean(image_convert_reshape, axis=0)
	image_convert_reshape_sd = np.std(image_convert_reshape, axis=0)
	image_convert_reshape = (image_convert_reshape - image_convert_reshape_mean) / image_convert_reshape_sd

	image_convert_reshape[:, 3:5] *= spatial_weight

	kmeans = MiniBatchKMeans(n_clusters=2, init='k-means++', n_init=3).fit(image_convert_reshape)
	mask = np.reshape(kmeans.labels_, (dim1, dim2)).astype(np.uint8)


	if mask[dim1 // 2, dim2 // 2] == 0:
		mask = 1 - mask

	masked_image = image * mask[..., np.newaxis]

	return mask, masked_image


def return_masked_image(image,spatial_weight):
	dim1 = image.shape[0]; dim2 = image.shape[1];
	image_convert = cv2.cvtColor(image, cv2.COLOR_BGR2YCR_CB)

	na, nb = (dim2, dim1)
	a = np.linspace(1, dim2, na)
	b = np.linspace(1, dim1, nb)
	xb, xa = np.meshgrid(a,b)
	xb = np.reshape(xb,(dim1,dim2,1)); xa = np.reshape(xa,(dim1,dim2,1));
	#plt.imshow(xa); plt.title("Color Transformed Image"); plt.show();
	image_convert_concat = np.concatenate((image_convert,xb,xa),axis = 2)

	image_convert_reshape = np.reshape(image_convert_concat, (dim1*dim2,5))
	image_convert_reshape_mean = np.mean(image_convert_reshape,axis=0)
	image_convert_reshape_sd = np.std(image_convert_reshape,axis=0)

	image_convert_reshape = (image_convert_reshape-image_convert_reshape_mean)/image_convert_reshape_sd
	image_convert_reshape[:,3] = spatial_weight*image_convert_reshape[:,3];
	image_convert_reshape[:,4] = spatial_weight*image_convert_reshape[:,4];

	kmeans = KMeans(n_clusters=2, init='k-means++').fit(image_convert_reshape)
	mask = np.reshape(kmeans.labels_, (dim1,dim2,1))
	if mask[int(dim1/2),int(dim2/2),0] == 0:
		mask = 1-mask

	masked_image = np.multiply(image,mask)
	return masked_image

def sharp_mask(image, kernel_size=(3, 3), sigma=1.0, amount=1.0, threshold=0):
	"""Return a sharpened version of the image, using an unsharp mask."""
	blurred = cv2.GaussianBlur(image, kernel_size, sigma)
	sharpened = float(amount + 1) * image - float(amount) * blurred
	sharpened = np.maximum(sharpened, np.zeros(sharpened.shape))
	sharpened = np.minimum(sharpened, 255 * np.ones(sharpened.shape))
	sharpened = sharpened.round().astype(np.uint8)
	if threshold > 0:
		low_contrast_mask = np.absolute(image - blurred) < threshold
		np.copyto(sharpened, image, where=low_contrast_mask)
	return sharpened

# def apply_adaptive_mean_thresholding(image,method="GAUSSIAN",block_size=7,subtraction_const=1):
#
# 	image_gray = cv2.cvtColor((image*255).astype(np.uint8), cv2.COLOR_BGR2GRAY)
#
# 	if method == "GAUSSIAN":
# 		thresholded_image = cv2.adaptiveThreshold(image_gray, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C,cv2.THRESH_BINARY, block_size, subtraction_const)
#
# 	elif method == "MEAN":
# 		thresholded_image = cv2.adaptiveThreshold(image_gray, 255, cv2.ADAPTIVE_THRESH_MEAN_C,cv2.THRESH_BINARY, block_size, subtraction_const)
#
# 	else:
# 		return;
#
# 	return thresholded_image.astype(np.float64)/255.0


def apply_adaptive_mean_thresholding(image,method="GAUSSIAN",block_size=7,subtraction_const=1):
	"""NOTE: block size must be an odd number"""

	image_gray = cv2.cvtColor((image*255).astype(np.uint8), cv2.COLOR_BGR2GRAY)
	# clahe = cv2.createCLAHE(clipLimit = 2)
	# image_gray = clahe.apply(image_gray) + 30

	if method == "GAUSSIAN":
		thresholded_image = cv2.adaptiveThreshold(image_gray, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
												  cv2.THRESH_BINARY, block_size, subtraction_const)

	elif method == "MEAN":
		thresholded_image = cv2.adaptiveThreshold(image_gray, 255, cv2.ADAPTIVE_THRESH_MEAN_C,
												  cv2.THRESH_BINARY, block_size, subtraction_const)

	else: print("Wrong method name used!"); return;

	return thresholded_image.astype(np.float64)/255.0


#old enhanceing
# class FingerprintImageEnhancer(object):
# 	def __init__(self):
# 		self.ridge_segment_blksze = 64
# 		self.ridge_segment_thresh = 0.05
# 		self.gradient_sigma = 1
# 		self.block_sigma = 7
# 		self.orient_smooth_sigma = 7
# 		self.ridge_freq_blksze = 38
# 		self.ridge_freq_windsze = 5
# 		self.min_wave_length = 1
# 		self.max_wave_length = 90
# 		self.kx = 0.75
# 		self.ky = 0.75
# 		self.angleInc = 3
# 		self.ridge_filter_thresh = -3
#
#
# 		self._mask = []
# 		self._normim = []
# 		self._orientim = []
# 		self._mean_freq = []
# 		self._median_freq = []
# 		self._freq = []
# 		self._freqim = []
# 		self._binim = []
#
# 	def __normalise(self, img, mean, std):
# 		if(np.std(img) == 0):
# 			raise ValueError("Image standard deviation is 0. Please review image again")
# 		normed = (img - np.mean(img)) / (np.std(img))
# 		return (normed)
#
# 	def __ridge_segment(self, img):
# 		rows, cols = img.shape
# 		im = self.__normalise(img, 0, 1)
# 		new_rows = int(self.ridge_segment_blksze * np.ceil((float(rows)) / (float(self.ridge_segment_blksze))))
# 		new_cols = int(self.ridge_segment_blksze * np.ceil((float(cols)) / (float(self.ridge_segment_blksze))))
# 		padded_img = np.zeros((new_rows, new_cols))
# 		stddevim = np.zeros((new_rows, new_cols))
# 		padded_img[0:rows][:, 0:cols] = im
# 		for i in range(0, new_rows, self.ridge_segment_blksze):
# 			for j in range(0, new_cols, self.ridge_segment_blksze):
# 				block = padded_img[i:i + self.ridge_segment_blksze][:, j:j + self.ridge_segment_blksze]
# 				stddevim[i:i + self.ridge_segment_blksze][:, j:j + self.ridge_segment_blksze] = np.std(block) * np.ones(block.shape)
# 		stddevim = stddevim[0:rows][:, 0:cols]
# 		self._mask = stddevim > self.ridge_segment_thresh
# 		mean_val = np.mean(im[self._mask])
# 		std_val = np.std(im[self._mask])
# 		self._normim = (im - mean_val) / (std_val)
#
# 	def __ridge_orient(self):
# 		rows,cols = self._normim.shape
# 		sze = np.fix(6*self.gradient_sigma)
# 		if np.remainder(sze,2) == 0:
# 			sze = sze+1
# 		gauss = cv2.getGaussianKernel(int(sze),self.gradient_sigma)
# 		f = gauss * gauss.T
# 		fy,fx = np.gradient(f)
# 		Gx = signal.convolve2d(self._normim, fx, mode='same')
# 		Gy = signal.convolve2d(self._normim, fy, mode='same')
# 		Gxx = np.power(Gx,2)
# 		Gyy = np.power(Gy,2)
# 		Gxy = Gx*Gy
# 		sze = np.fix(6*self.block_sigma)
# 		gauss = cv2.getGaussianKernel(int(sze), self.block_sigma)
# 		f = gauss * gauss.T
# 		Gxx = ndimage.convolve(Gxx,f)
# 		Gyy = ndimage.convolve(Gyy,f)
# 		Gxy = 2*ndimage.convolve(Gxy,f)
# 		denom = np.sqrt(np.power(Gxy,2) + np.power((Gxx - Gyy),2)) + np.finfo(float).eps
# 		sin2theta = Gxy/denom
# 		cos2theta = (Gxx-Gyy)/denom
# 		if self.orient_smooth_sigma:
# 			sze = np.fix(6*self.orient_smooth_sigma)
# 			if np.remainder(sze,2) == 0:
# 				sze = sze+1
# 			gauss = cv2.getGaussianKernel(int(sze), self.orient_smooth_sigma)
# 			f = gauss * gauss.T
# 			cos2theta = ndimage.convolve(cos2theta,f)
# 			sin2theta = ndimage.convolve(sin2theta,f)
# 		self._orientim = np.pi/2 + np.arctan2(sin2theta,cos2theta)/2
#
# 	def __ridge_freq(self):
# 		rows, cols = self._normim.shape
# 		freq = np.zeros((rows, cols))
# 		for r in range(0, rows - self.ridge_freq_blksze, self.ridge_freq_blksze):
# 			for c in range(0, cols - self.ridge_freq_blksze, self.ridge_freq_blksze):
# 				blkim = self._normim[r:r + self.ridge_freq_blksze][:, c:c + self.ridge_freq_blksze]
# 				blkor = self._orientim[r:r + self.ridge_freq_blksze][:, c:c + self.ridge_freq_blksze]
# 				freq[r:r + self.ridge_freq_blksze][:, c:c + self.ridge_freq_blksze] = self.__frequest(blkim, blkor)
# 		self._freq = freq * self._mask
# 		freq_1d = np.reshape(self._freq, (1, rows * cols))
# 		ind = np.where(freq_1d > 0)
# 		ind = np.array(ind)
# 		ind = ind[1, :]
# 		non_zero_elems_in_freq = freq_1d[0][ind]
# 		self._mean_freq = np.mean(non_zero_elems_in_freq)
# 		self._median_freq = np.median(non_zero_elems_in_freq)
# 		self._freq = self._mean_freq * self._mask
#
# 	def __frequest(self, blkim, blkor):
# 		rows, cols = np.shape(blkim)
# 		cosorient = np.mean(np.cos(2 * blkor))
# 		sinorient = np.mean(np.sin(2 * blkor))
# 		orient = math.atan2(sinorient, cosorient) / 2
# 		rotim = scipy.ndimage.rotate(blkim, orient / np.pi * 180 + 90, axes=(1, 0), reshape=False, order=3,mode='nearest')
# 		cropsze = int(np.fix(rows / np.sqrt(2)))
# 		offset = int(np.fix((rows - cropsze) / 2))
# 		rotim = rotim[offset:offset + cropsze][:, offset:offset + cropsze]
# 		proj = np.sum(rotim, axis=0)
# 		dilation = scipy.ndimage.grey_dilation(proj, self.ridge_freq_windsze, structure=np.ones(self.ridge_freq_windsze))
# 		temp = np.abs(dilation - proj)
# 		peak_thresh = 2
# 		maxpts = (temp < peak_thresh) & (proj > np.mean(proj))
# 		maxind = np.where(maxpts)
# 		rows_maxind, cols_maxind = np.shape(maxind)
# 		if (cols_maxind < 2):
# 			return(np.zeros(blkim.shape))
# 		else:
# 			NoOfPeaks = cols_maxind
# 			waveLength = (maxind[0][cols_maxind - 1] - maxind[0][0]) / (NoOfPeaks - 1)
# 			if waveLength >= self.min_wave_length and waveLength <= self.max_wave_length:
# 				return(1 / np.double(waveLength) * np.ones(blkim.shape))
# 			else:
# 				return(np.zeros(blkim.shape))
#
# 	def __ridge_filter(self):
# 		im = np.double(self._normim)
# 		rows, cols = im.shape
# 		newim = np.zeros((rows, cols))
# 		freq_1d = np.reshape(self._freq, (1, rows * cols))
# 		ind = np.where(freq_1d > 0)
# 		ind = np.array(ind)
# 		ind = ind[1, :]
# 		non_zero_elems_in_freq = freq_1d[0][ind]
# 		non_zero_elems_in_freq = np.double(np.round((non_zero_elems_in_freq * 100))) / 100
# 		unfreq = np.unique(non_zero_elems_in_freq)
# 		sigmax = 1 / unfreq[0] * self.kx
# 		sigmay = 1 / unfreq[0] * self.ky
# 		sze = int(np.round(3 * np.max([sigmax, sigmay])))
# 		x, y = np.meshgrid(np.linspace(-sze, sze, (2 * sze + 1)), np.linspace(-sze, sze, (2 * sze + 1)))
# 		reffilter = np.exp(-(((np.power(x, 2)) / (sigmax * sigmax) + (np.power(y, 2)) / (sigmay * sigmay)))) * np.cos(
# 			2 * np.pi * unfreq[0] * x)
# 		filt_rows, filt_cols = reffilter.shape
# 		angleRange = int(180 / self.angleInc)
# 		gabor_filter = np.array(np.zeros((angleRange, filt_rows, filt_cols)))
# 		for o in range(0, angleRange):
# 			rot_filt = scipy.ndimage.rotate(reffilter, -(o * self.angleInc + 90), reshape=False)
# 			gabor_filter[o] = rot_filt
# 		maxsze = int(sze)
# 		temp = self._freq > 0
# 		validr, validc = np.where(temp)
# 		temp1 = validr > maxsze
# 		temp2 = validr < rows - maxsze
# 		temp3 = validc > maxsze
# 		temp4 = validc < cols - maxsze
# 		final_temp = temp1 & temp2 & temp3 & temp4
# 		finalind = np.where(final_temp)
# 		maxorientindex = np.round(180 / self.angleInc)
# 		orientindex = np.round(self._orientim / np.pi * 180 / self.angleInc)
# 		for i in range(0, rows):
# 			for j in range(0, cols):
# 				if (orientindex[i][j] < 1):
# 					orientindex[i][j] = orientindex[i][j] + maxorientindex
# 				if (orientindex[i][j] > maxorientindex):
# 					orientindex[i][j] = orientindex[i][j] - maxorientindex
# 		finalind_rows, finalind_cols = np.shape(finalind)
# 		sze = int(sze)
# 		for k in range(0, finalind_cols):
# 			r = validr[finalind[0][k]]
# 			c = validc[finalind[0][k]]
# 			img_block = im[r - sze:r + sze + 1][:, c - sze:c + sze + 1]
# 			newim[r][c] = np.sum(img_block * gabor_filter[int(orientindex[r][c]) - 1])
# 		self._binim = newim < self.ridge_filter_thresh
#
# 	def save_enhanced_image(self, path):
# 		cv2.imwrite(path, (255 * self._binim))
#
# 	def enhance(self, img, resize=True):
# 		if(resize):
# 			rows, cols = np.shape(img)
# 			aspect_ratio = np.double(rows) / np.double(cols)
# 			new_rows = 350
# 			new_cols = new_rows / aspect_ratio
# 			img = cv2.resize(img, (int(new_cols), int(new_rows)))
# 		self.__ridge_segment(img)
# 		self.__ridge_orient()
# 		self.__ridge_freq()
# 		self.__ridge_filter()
# 		return(self._binim)

#new enhancing
class FingerprintImageEnhancer(object):
	def __init__(self):
		# self.ridge_segment_blksze = 16
		# self.ridge_segment_thresh = 0.1
		# self.gradient_sigma = 1
		# self.block_sigma = 7
		# self.orient_smooth_sigma = 7
		# self.ridge_freq_blksze = 38
		# self.ridge_freq_windsze = 5
		# self.min_wave_length = 5
		# self.max_wave_length = 15
		# self.kx = 0.65
		# self.ky = 0.65
		# self.angleInc = 3
		# self.ridge_filter_thresh = -3

#above values for siamese + scat model
		self.ridge_segment_blksze = 32
		self.ridge_segment_thresh = 0.1
		self.gradient_sigma = 1
		self.block_sigma = 7
		self.orient_smooth_sigma = 7
		self.ridge_freq_blksze = 38
		self.ridge_freq_windsze = 5
		self.min_wave_length = 5
		self.max_wave_length = 15
		self.kx = 1.0
		self.ky = 1.0
		self.angleInc = 3
		self.ridge_filter_thresh = -1


		self._mask = []
		self._normim = []
		self._orientim = []
		self._mean_freq = []
		self._median_freq = []
		self._freq = []
		self._freqim = []
		self._binim = []

	def __normalise(self, img, mean, std):
		if(np.std(img) == 0):
			raise ValueError("Image standard deviation is 0. Please review image again")
		normed = (img - np.mean(img)) / (np.std(img))
		return (normed)

	def __ridge_segment(self, img):

		rows, cols = img.shape
		im = self.__normalise(img, 0, 1)  # normalise to get zero mean and unit standard deviation

		new_rows = int(self.ridge_segment_blksze * np.ceil((float(rows)) / (float(self.ridge_segment_blksze))))
		new_cols = int(self.ridge_segment_blksze * np.ceil((float(cols)) / (float(self.ridge_segment_blksze))))

		padded_img = np.zeros((new_rows, new_cols))
		stddevim = np.zeros((new_rows, new_cols))
		padded_img[0:rows][:, 0:cols] = im
		for i in range(0, new_rows, self.ridge_segment_blksze):
			for j in range(0, new_cols, self.ridge_segment_blksze):
				block = padded_img[i:i + self.ridge_segment_blksze][:, j:j + self.ridge_segment_blksze]

				stddevim[i:i + self.ridge_segment_blksze][:, j:j + self.ridge_segment_blksze] = np.std(block) * np.ones(block.shape)

		stddevim = stddevim[0:rows][:, 0:cols]
		self._mask = stddevim > self.ridge_segment_thresh
		mean_val = np.mean(im[self._mask])
		std_val = np.std(im[self._mask])
		self._normim = (im - mean_val) / (std_val)

	def __ridge_orient(self):

		rows,cols = self._normim.shape
		#Calculate image gradients.
		sze = np.fix(6*self.gradient_sigma)
		if np.remainder(sze,2) == 0:
			sze = sze+1

		gauss = cv2.getGaussianKernel(int(sze),self.gradient_sigma)
		f = gauss * gauss.T

		fy,fx = np.gradient(f)                               #Gradient of Gaussian

		Gx = signal.convolve2d(self._normim, fx, mode='same')
		Gy = signal.convolve2d(self._normim, fy, mode='same')

		Gxx = np.power(Gx,2)
		Gyy = np.power(Gy,2)
		Gxy = Gx*Gy

		#Now smooth the covariance data to perform a weighted summation of the data.
		sze = np.fix(6*self.block_sigma)

		gauss = cv2.getGaussianKernel(int(sze), self.block_sigma)
		f = gauss * gauss.T

		Gxx = ndimage.convolve(Gxx,f)
		Gyy = ndimage.convolve(Gyy,f)
		Gxy = 2*ndimage.convolve(Gxy,f)

		# Analytic solution of principal direction
		denom = np.sqrt(np.power(Gxy,2) + np.power((Gxx - Gyy),2)) + np.finfo(float).eps

		sin2theta = Gxy/denom                   # Sine and cosine of doubled angles
		cos2theta = (Gxx-Gyy)/denom


		if self.orient_smooth_sigma:
			sze = np.fix(6*self.orient_smooth_sigma)
			if np.remainder(sze,2) == 0:
				sze = sze+1
			gauss = cv2.getGaussianKernel(int(sze), self.orient_smooth_sigma)
			f = gauss * gauss.T
			cos2theta = ndimage.convolve(cos2theta,f)                   # Smoothed sine and cosine of
			sin2theta = ndimage.convolve(sin2theta,f)                   # doubled angles

		self._orientim = np.pi/2 + np.arctan2(sin2theta,cos2theta)/2

	def __ridge_freq(self):

		rows, cols = self._normim.shape
		freq = np.zeros((rows, cols))

		for r in range(0, rows - self.ridge_freq_blksze, self.ridge_freq_blksze):
			for c in range(0, cols - self.ridge_freq_blksze, self.ridge_freq_blksze):
				blkim = self._normim[r:r + self.ridge_freq_blksze][:, c:c + self.ridge_freq_blksze]
				blkor = self._orientim[r:r + self.ridge_freq_blksze][:, c:c + self.ridge_freq_blksze]

				freq[r:r + self.ridge_freq_blksze][:, c:c + self.ridge_freq_blksze] = self.__frequest(blkim, blkor)

		self._freq = freq * self._mask
		freq_1d = np.reshape(self._freq, (1, rows * cols))
		ind = np.where(freq_1d > 0)

		ind = np.array(ind)
		ind = ind[1, :]

		non_zero_elems_in_freq = freq_1d[0][ind]

		self._mean_freq = np.mean(non_zero_elems_in_freq)
		self._median_freq = np.median(non_zero_elems_in_freq)  # does not work properly

		self._freq = self._mean_freq * self._mask

	def __frequest(self, blkim, blkor):

		rows, cols = np.shape(blkim)


		cosorient = np.mean(np.cos(2 * blkor))
		sinorient = np.mean(np.sin(2 * blkor))
		orient = math.atan2(sinorient, cosorient) / 2


		rotim = scipy.ndimage.rotate(blkim, orient / np.pi * 180 + 90, axes=(1, 0), reshape=False, order=3,
									 mode='nearest')


		cropsze = int(np.fix(rows / np.sqrt(2)))
		offset = int(np.fix((rows - cropsze) / 2))
		rotim = rotim[offset:offset + cropsze][:, offset:offset + cropsze]



		proj = np.sum(rotim, axis=0)
		dilation = scipy.ndimage.grey_dilation(proj, self.ridge_freq_windsze, structure=np.ones(self.ridge_freq_windsze))

		temp = np.abs(dilation - proj)

		peak_thresh = 2

		maxpts = (temp < peak_thresh) & (proj > np.mean(proj))
		maxind = np.where(maxpts)

		rows_maxind, cols_maxind = np.shape(maxind)



		if (cols_maxind < 2):
			return(np.zeros(blkim.shape))
		else:
			NoOfPeaks = cols_maxind
			waveLength = (maxind[0][cols_maxind - 1] - maxind[0][0]) / (NoOfPeaks - 1)
			if waveLength >= self.min_wave_length and waveLength <= self.max_wave_length:
				return(1 / np.double(waveLength) * np.ones(blkim.shape))
			else:
				return(np.zeros(blkim.shape))

	def __ridge_filter(self):


		im = np.double(self._normim)
		rows, cols = im.shape
		newim = np.zeros((rows, cols))

		freq_1d = np.reshape(self._freq, (1, rows * cols))
		ind = np.where(freq_1d > 0)

		ind = np.array(ind)
		ind = ind[1, :]

		# Round the array of frequencies to the nearest 0.01 to reduce the
		# number of distinct frequencies we have to deal with.

		non_zero_elems_in_freq = freq_1d[0][ind]
		non_zero_elems_in_freq = np.double(np.round((non_zero_elems_in_freq * 100))) / 100

		unfreq = np.unique(non_zero_elems_in_freq)

		# Generate filters corresponding to these distinct frequencies and
		# orientations in 'angleInc' increments.

		sigmax = 1 / unfreq[0] * self.kx
		sigmay = 1 / unfreq[0] * self.ky

		sze = int(np.round(3 * np.max([sigmax, sigmay])))

		x, y = np.meshgrid(np.linspace(-sze, sze, (2 * sze + 1)), np.linspace(-sze, sze, (2 * sze + 1)))

		reffilter = np.exp(-(((np.power(x, 2)) / (sigmax * sigmax) + (np.power(y, 2)) / (sigmay * sigmay)))) * np.cos(
			2 * np.pi * unfreq[0] * x)        # this is the original gabor filter

		filt_rows, filt_cols = reffilter.shape

		angleRange = int(180 / self.angleInc)

		gabor_filter = np.array(np.zeros((angleRange, filt_rows, filt_cols)))

		for o in range(0, angleRange):
			# Generate rotated versions of the filter.  Note orientation
			# image provides orientation *along* the ridges, hence +90
			# degrees, and imrotate requires angles +ve anticlockwise, hence
			# the minus sign.

			rot_filt = scipy.ndimage.rotate(reffilter, -(o * self.angleInc + 90), reshape=False)
			gabor_filter[o] = rot_filt

		# Find indices of matrix points greater than maxsze from the image
		# boundary

		maxsze = int(sze)

		temp = self._freq > 0
		validr, validc = np.where(temp)

		temp1 = validr > maxsze
		temp2 = validr < rows - maxsze
		temp3 = validc > maxsze
		temp4 = validc < cols - maxsze

		final_temp = temp1 & temp2 & temp3 & temp4

		finalind = np.where(final_temp)

		# Convert orientation matrix values from radians to an index value
		# that corresponds to round(degrees/angleInc)

		maxorientindex = np.round(180 / self.angleInc)
		orientindex = np.round(self._orientim / np.pi * 180 / self.angleInc)

		# do the filtering
		for i in range(0, rows):
			for j in range(0, cols):
				if (orientindex[i][j] < 1):
					orientindex[i][j] = orientindex[i][j] + maxorientindex
				if (orientindex[i][j] > maxorientindex):
					orientindex[i][j] = orientindex[i][j] - maxorientindex
		finalind_rows, finalind_cols = np.shape(finalind)
		sze = int(sze)
		for k in range(0, finalind_cols):
			r = validr[finalind[0][k]]
			c = validc[finalind[0][k]]

			img_block = im[r - sze:r + sze + 1][:, c - sze:c + sze + 1]

			newim[r][c] = np.sum(img_block * gabor_filter[int(orientindex[r][c]) - 1])

		self._binim = newim < self.ridge_filter_thresh

	def save_enhanced_image(self, path):
		# saves the enhanced image at the specified path
		cv2.imwrite(path, (255 * self._binim))

	def enhance(self, img, resize=True):
		# main function to enhance the image.
		# calls all other subroutines

		if(resize):
			rows, cols = np.shape(img)
			aspect_ratio = np.double(rows) / np.double(cols)

			new_rows = 450                      # 350 for old siamese + scat best moel
			new_cols = new_rows / aspect_ratio

			img = cv2.resize(img, (int(new_cols), int(new_rows)))

		self.__ridge_segment(img)   # normalise the image and find a ROI
		self.__ridge_orient()       # compute orientation image
		self.__ridge_freq()         # compute major frequency of ridges
		self.__ridge_filter()       # filter the image using oriented gabor filter
		return(self._binim)

class MinutiaeFeature(object):
	def __init__(self, locX, locY, Orientation, Type):
		self.locX = locX
		self.locY = locY
		self.Orientation = Orientation
		self.Type = Type

class FingerprintFeatureExtractor(object):
	def __init__(self):
		self._mask = []
		self._skel = []
		# self.minutiaeTerm = []
		self.minutiaeBif = []
		self._spuriousMinutiaeThresh = 10

	def setSpuriousMinutiaeThresh(self, spuriousMinutiaeThresh):
		self._spuriousMinutiaeThresh = spuriousMinutiaeThresh

	def __skeletonize(self, img):
		img = np.uint8(img > 128)
		self._skel = skimage.morphology.skeletonize(img)
		self._skel = np.uint8(self._skel) * 255
		self._mask = img * 255
		return (img)

	def __computeAngle(self, block, minutiaeType):
		angle = []
		(blkRows, blkCols) = np.shape(block)
		CenterX, CenterY = (blkRows - 1) / 2, (blkCols - 1) / 2
		# if (minutiaeType.lower() == 'termination'):
		#     sumVal = 0
		#     for i in range(blkRows):
		#         for j in range(blkCols):
		#             if ((i == 0 or i == blkRows - 1 or j == 0 or j == blkCols - 1) and block[i][j] != 0):
		#                 angle.append(-math.degrees(math.atan2(i - CenterY, j - CenterX)))
		#                 sumVal += 1
		#                 if (sumVal > 1):
		#                     angle.append(float('nan'))
		#     return (angle)

		if (minutiaeType.lower() == 'bifurcation'):
			(blkRows, blkCols) = np.shape(block)
			CenterX, CenterY = (blkRows - 1) / 2, (blkCols - 1) / 2
			angle = []
			sumVal = 0
			for i in range(blkRows):
				for j in range(blkCols):
					if ((i == 0 or i == blkRows - 1 or j == 0 or j == blkCols - 1) and block[i][j] != 0):
						angle.append(-math.degrees(math.atan2(i - CenterY, j - CenterX)))
						sumVal += 1
			if (sumVal != 3):
				angle.append(float('nan'))
			return (angle)

	def __getTerminationBifurcation(self):
		self._skel = self._skel == 255
		(rows, cols) = self._skel.shape
		# self.minutiaeTerm = np.zeros(self._skel.shape)
		self.minutiaeBif = np.zeros(self._skel.shape)

		for i in range(1, rows - 1):
			for j in range(1, cols - 1):
				if (self._skel[i][j] == 1):
					block = self._skel[i - 1:i + 2, j - 1:j + 2]
					block_val = np.sum(block)
					# if (block_val == 2):
					#     self.minutiaeTerm[i, j] = 1
					if (block_val == 4):
						self.minutiaeBif[i, j] = 1

		self._mask = convex_hull_image(self._mask > 0)
		self._mask = erosion(self._mask, square(5))  # Structuing element for mask erosion = square(5)
		# self.minutiaeTerm = np.uint8(self._mask) * self.minutiaeTerm

	def __removeSpuriousMinutiae(self, minutiaeList, img):
		img = img * 0
		SpuriousMin = []
		numPoints = len(minutiaeList)
		D = np.zeros((numPoints, numPoints))
		for i in range(1,numPoints):
			for j in range(0, i):
				(X1,Y1) = minutiaeList[i]['centroid']
				(X2,Y2) = minutiaeList[j]['centroid']

				dist = np.sqrt((X2-X1)**2 + (Y2-Y1)**2)
				D[i][j] = dist
				if(dist < self._spuriousMinutiaeThresh):
					SpuriousMin.append(i)
					SpuriousMin.append(j)

		SpuriousMin = np.unique(SpuriousMin)
		for i in range(0,numPoints):
			if(not i in SpuriousMin):
				(X,Y) = np.int16(minutiaeList[i]['centroid'])
				img[X,Y] = 1

		img = np.uint8(img)
		return(img)

	# def __cleanMinutiae(self, img):
	# self.minutiaeTerm = skimage.measure.label(self.minutiaeTerm, connectivity=2)
	# RP = skimage.measure.regionprops(self.minutiaeTerm)
	# self.minutiaeTerm = self.__removeSpuriousMinutiae(RP, np.uint8(img))

	def __performFeatureExtraction(self):
		# FeaturesTerm = []
		# self.minutiaeTerm = skimage.measure.label(self.minutiaeTerm, connectivity=2)
		# RP = skimage.measure.regionprops(np.uint8(self.minutiaeTerm))

		# WindowSize = 2  # --> For Termination, the block size must can be 3x3, or 5x5. Hence the window selected is 1 or 2
		FeaturesTerm = []
		# for num, i in enumerate(RP):
		#     (row, col) = np.int16(np.round(i['Centroid']))
		#     block = self._skel[row - WindowSize:row + WindowSize + 1, col - WindowSize:col + WindowSize + 1]
		#     angle = self.__computeAngle(block, 'Termination')
		#     if(len(angle) == 1):
		#         FeaturesTerm.append(MinutiaeFeature(row, col, angle, 'Termination'))

		FeaturesBif = []
		self.minutiaeBif = skimage.measure.label(self.minutiaeBif, connectivity=2)
		RP = skimage.measure.regionprops(np.uint8(self.minutiaeBif))
		WindowSize = 1  # --> For Bifurcation, the block size must be 3x3. Hence the window selected is 1
		for i in RP:
			(row, col) = np.int16(np.round(i['Centroid']))
			block = self._skel[row - WindowSize:row + WindowSize + 1, col - WindowSize:col + WindowSize + 1]
			angle = self.__computeAngle(block, 'Bifurcation')
			if(len(angle) == 3):
				FeaturesBif.append(MinutiaeFeature(row, col, angle, 'Bifurcation'))
		return (FeaturesTerm, FeaturesBif)

	def extractMinutiaeFeatures(self, img):
		self.__skeletonize(img)
		self.__getTerminationBifurcation()
		# self.__cleanMinutiae(img)
		FeaturesTerm, FeaturesBif = self.__performFeatureExtraction()
		return(FeaturesTerm, FeaturesBif)

	def showResults(self, FeaturesTerm, FeaturesBif):

		(rows, cols) = self._skel.shape
		DispImg = np.zeros((rows, cols, 3), np.uint8)
		DispImg[:, :, 0] = 255*self._skel
		DispImg[:, :, 1] = 255*self._skel
		DispImg[:, :, 2] = 255*self._skel

		# for idx, curr_minutiae in enumerate(FeaturesTerm):
		#     row, col = curr_minutiae.locX, curr_minutiae.locY
		#     (rr, cc) = skimage.draw.circle_perimeter(row, col, 3)
		#     skimage.draw.set_color(DispImg, (rr, cc), (0, 0, 255))

		for idx, curr_minutiae in enumerate(FeaturesBif):
			row, col = curr_minutiae.locX, curr_minutiae.locY
			(rr, cc) = skimage.draw.circle_perimeter(row, col, 3)
			skimage.draw.set_color(DispImg, (rr, cc), (255, 0, 0))

		# cv2_imshow(DispImg)
		# cv2.waitKey(0)
		return DispImg

	def saveResult(self, FeaturesTerm, FeaturesBif):
		(rows, cols) = self._skel.shape
		DispImg = np.zeros((rows, cols, 3), np.uint8)
		DispImg[:, :, 0] = 255 * self._skel
		DispImg[:, :, 1] = 255 * self._skel
		DispImg[:, :, 2] = 255 * self._skel

		for idx, curr_minutiae in enumerate(FeaturesTerm):
			row, col = curr_minutiae.locX, curr_minutiae.locY
			(rr, cc) = skimage.draw.circle_perimeter(row, col, 3)
			skimage.draw.set_color(DispImg, (rr, cc), (0, 0, 255))

		for idx, curr_minutiae in enumerate(FeaturesBif):
			row, col = curr_minutiae.locX, curr_minutiae.locY
			(rr, cc) = skimage.draw.circle_perimeter(row, col, 3)
			skimage.draw.set_color(DispImg, (rr, cc), (255, 0, 0))

		# cv2.imwrite('result.png', DispImg)

def extract_minutiae_features(img, spuriousMinutiaeThresh, invertImage, showResult, saveResult):
	feature_extractor = FingerprintFeatureExtractor()
	feature_extractor.setSpuriousMinutiaeThresh(spuriousMinutiaeThresh)
	if (invertImage):
		img = 255 - img;

	FeaturesTerm, FeaturesBif = feature_extractor.extractMinutiaeFeatures(img)

	# if (saveResult):
	#     feature_extractor.saveResult(FeaturesTerm, FeaturesBif)

	if(showResult):
		img = feature_extractor.showResults(FeaturesTerm, FeaturesBif)

	return img


#------------------MATCHING--------------
# def matching():
# 	print(tf.__version__)
# 	print(keras.__version__)
# 	filename = join(dirname(__file__), "model_siamese_net1_r.h5")
#
# 	# filename = dirname(__file__)
# 	# with open(filename,'r',encoding='utf8',errors='ignore') as fin:
# 		# print(fin)
# 	# p = load_model(filename)
# 	print("modelread")
